package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.parking.dto.ParkingDtos.SpotDto;
import fr.epita.platform.apps.parking.dto.ParkingDtos.SpotRequest;
import fr.epita.platform.apps.parking.service.ParkingService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.CalendarRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParkingServiceTest extends IntegrationTest {

    @Autowired
    ParkingService service;

    AuthUser admin;
    AuthUser holder;
    AuthUser alice;
    AuthUser bob;
    SpotDto spot;
    LocalDate day;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        holder = auth(user("Hugo Titulaire"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        spot = service.create(admin, new SpotRequest("A-1", "-1"));
        service.setHolder(admin, spot.id(), holder.id());
        day = LocalDate.now().plusDays(1);
        while (!CalendarRules.isWorkingDay(day)) {
            day = day.plusDays(1);
        }
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void onlyReleasedSpotsCanBeBooked() {
        assertCode(() -> service.book(alice, day, null), "SPOT_ALREADY_TAKEN");
        service.release(holder, List.of(day));
        assertThat(service.book(alice, day, null).spotNumber()).isEqualTo("A-1");
        assertCode(() -> service.book(bob, day, spot.id()), "SPOT_ALREADY_TAKEN");
        assertCode(() -> service.takeBack(holder, day), "SPOT_ALREADY_TAKEN");
        assertThat(service.day(holder, day).spots().get(0).state()).isEqualTo("BOOKED");
    }

    @Test
    void oneBookingPerDayAndHolderRules() {
        service.release(holder, List.of(day));
        SpotDto free = service.create(admin, new SpotRequest("A-2", "-1"));
        service.book(alice, day, free.id());
        assertCode(() -> service.book(alice, day, null), "ALREADY_BOOKED_TODAY");
        AuthUser other = auth(user("Olga Titulaire"));
        SpotDto third = service.create(admin, new SpotRequest("A-3", "-1"));
        service.setHolder(admin, third.id(), other.id());
        assertCode(() -> service.book(other, day, null), "HOLDER_HAS_SPOT");
        assertCode(() -> service.setHolder(admin, free.id(), other.id()), "HOLDER_ALREADY_HAS_SPOT");
        service.takeBack(holder, day);
        assertThat(service.me(holder).releases()).isEmpty();
    }

    @Test
    void bookingWindowAndWorkingDays() {
        SpotDto free = service.create(admin, new SpotRequest("B-1", "0"));
        LocalDate far = LocalDate.now().plusDays(12);
        while (!CalendarRules.isWorkingDay(far)) {
            far = far.plusDays(1);
        }
        LocalDate farDay = far;
        assertCode(() -> service.book(alice, farDay, free.id()), "BOOKING_TOO_EARLY");
        LocalDate saturday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY));
        assertThatThrownBy(() -> service.book(alice, saturday, free.id())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.release(holder, List.of(LocalDate.now().minusDays(1)))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void concurrentBookingsOfTheLastSpotOnlyOneSucceeds() throws Exception {
        service.release(holder, List.of(day));
        LocalDate d = day;
        Callable<Boolean> a = () -> { try { service.book(alice, d, spot.id()); return true; } catch (ApiException e) { return false; } };
        Callable<Boolean> b = () -> { try { service.book(bob, d, spot.id()); return true; } catch (ApiException e) { return false; } };
        var pool = Executors.newFixedThreadPool(2);
        var fa = pool.submit(a);
        var fb = pool.submit(b);
        int ok = (fa.get() ? 1 : 0) + (fb.get() ? 1 : 0);
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
    }
}
