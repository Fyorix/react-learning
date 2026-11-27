package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.room.dto.RoomDtos.BookingDto;
import fr.epita.platform.apps.room.dto.RoomDtos.BookingRequest;
import fr.epita.platform.apps.room.dto.RoomDtos.RoomDto;
import fr.epita.platform.apps.room.dto.RoomDtos.RoomRequest;
import fr.epita.platform.apps.room.entity.Equipment;
import fr.epita.platform.apps.room.service.RoomService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomServiceTest extends IntegrationTest {

    static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Autowired
    RoomService service;

    AuthUser admin;
    AuthUser alice;
    AuthUser bob;
    RoomDto room;
    LocalDate day;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        room = service.create(admin, new RoomRequest("Turing", "2e", 6, Set.of(Equipment.SCREEN), true));
        day = LocalDate.now(PARIS).plusDays(3);
    }

    private Instant at(int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(PARIS).toInstant();
    }

    private BookingRequest slot(int h1, int m1, int h2, int m2) {
        return new BookingRequest(at(h1, m1), at(h2, m2), "Réunion", 4);
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void overlappingBookingsAreRefusedButAdjacentOnesAreAllowed() {
        service.book(alice, room.id(), slot(9, 0, 10, 30));
        assertCode(() -> service.book(bob, room.id(), slot(10, 0, 11, 0)), "ROOM_ALREADY_BOOKED");
        assertThat(service.book(bob, room.id(), slot(10, 30, 11, 0)).start()).isEqualTo(at(10, 30));
    }

    @Test
    void slotRulesAreChecked() {
        assertThatThrownBy(() -> service.book(alice, room.id(), slot(9, 15, 10, 0))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.book(alice, room.id(), slot(7, 30, 9, 0))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.book(alice, room.id(), slot(9, 0, 13, 30))).isInstanceOf(ValidationException.class);
        assertCode(() -> service.book(alice, room.id(), new BookingRequest(at(9, 0), at(10, 0), "Grande réunion", 7)), "CAPACITY_EXCEEDED");
        day = LocalDate.now(PARIS).plusDays(40);
        assertCode(() -> service.book(alice, room.id(), slot(9, 0, 10, 0)), "BOOKING_OUT_OF_RANGE");
    }

    @Test
    void onlyAuthorOrAdminCanModifyOrCancel() {
        BookingDto b = service.book(alice, room.id(), slot(9, 0, 10, 0));
        assertCode(() -> service.cancel(bob, b.id()), "FORBIDDEN");
        assertThat(service.update(alice, b.id(), slot(9, 0, 10, 30)).end()).isEqualTo(at(10, 30));
        service.cancel(admin, b.id());
        assertThat(service.mine(alice)).isEmpty();
    }

    @Test
    void deactivatingARoomCancelsFutureBookings() {
        service.book(alice, room.id(), slot(9, 0, 10, 0));
        service.update(admin, room.id(), new RoomRequest("Turing", "2e", 6, Set.of(), false));
        assertThat(service.mine(alice)).isEmpty();
        assertCode(() -> service.book(alice, room.id(), slot(11, 0, 12, 0)), "ROOM_INACTIVE");
        assertThat(service.list(null, null, false)).isEmpty();
    }

    @Test
    void availableRoomsExcludeBookedOnes() {
        RoomDto other = service.create(admin, new RoomRequest("Lovelace", "1er", 12, Set.of(Equipment.VISIO), true));
        service.book(alice, room.id(), slot(14, 0, 15, 0));
        assertThat(service.available(at(14, 0), at(15, 0), null, null)).extracting(RoomDto::id).containsExactly(other.id());
        assertThat(service.available(at(15, 0), at(16, 0), 8, null)).extracting(RoomDto::id).containsExactly(other.id());
    }

    @Test
    void concurrentBookingsOfTheSameSlotOnlyOneSucceeds() throws Exception {
        Callable<Boolean> task = () -> {
            try {
                service.book(alice, room.id(), slot(16, 0, 17, 0));
                return true;
            } catch (ApiException e) {
                return false;
            }
        };
        var pool = Executors.newFixedThreadPool(2);
        Future<Boolean> a = pool.submit(task);
        Future<Boolean> b = pool.submit(task);
        int ok = (a.get() ? 1 : 0) + (b.get() ? 1 : 0);
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
    }
}
