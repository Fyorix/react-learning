package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.carpool.dto.CarpoolDtos.TripDto;
import fr.epita.platform.apps.carpool.dto.CarpoolDtos.TripRequest;
import fr.epita.platform.apps.carpool.service.CarpoolService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CarpoolServiceTest extends IntegrationTest {

    @Autowired
    CarpoolService service;

    private TripDto trip(AuthUser driver, int seats) {
        return service.create(driver, new TripRequest("Paris", "Lyon", Instant.now().plus(2, ChronoUnit.DAYS), seats, null));
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void driverCannotBookOwnTripAndPassengerOnlyOnce() {
        AuthUser driver = auth(user("Driver"));
        AuthUser alice = auth(user("Alice"));
        TripDto t = trip(driver, 3);

        assertCode(() -> service.book(driver, t.id()), "OWN_TRIP");
        assertThat(service.book(alice, t.id()).remainingSeats()).isEqualTo(2);
        assertCode(() -> service.book(alice, t.id()), "ALREADY_BOOKED");
    }

    @Test
    void noMoreBookingsThanSeats() {
        AuthUser driver = auth(user("Driver"));
        TripDto t = trip(driver, 1);
        service.book(auth(user("Alice")), t.id());
        assertCode(() -> service.book(auth(user("Bob")), t.id()), "NO_SEAT_AVAILABLE");
    }

    @Test
    void cancelledOrPastTripsAreNotBookable() {
        AuthUser driver = auth(user("Driver"));
        AuthUser alice = auth(user("Alice"));
        TripDto t = trip(driver, 2);
        service.cancel(driver, t.id());
        assertCode(() -> service.book(alice, t.id()), "TRIP_NOT_BOOKABLE");

        TripDto past = trip(driver, 2);
        jdbc.update("update carpool_trips set departure_at = now() - interval '1 hour' where id = ?", past.id());
        assertCode(() -> service.book(alice, past.id()), "TRIP_NOT_BOOKABLE");
    }

    @Test
    void seatsCannotGoBelowBookings() {
        AuthUser driver = auth(user("Driver"));
        TripDto t = trip(driver, 3);
        service.book(auth(user("Alice")), t.id());
        service.book(auth(user("Bob")), t.id());
        assertCode(() -> service.update(driver, t.id(),
                new TripRequest("Paris", "Lyon", t.departureAt(), 1, null)), "SEATS_BELOW_BOOKINGS");
    }

    @Test
    void onlyDriverOrAdminCanCancelAndPassengersAreHiddenFromOthers() {
        AuthUser driver = auth(user("Driver"));
        AuthUser alice = auth(user("Alice"));
        TripDto t = trip(driver, 3);
        service.book(alice, t.id());
        assertCode(() -> service.cancel(alice, t.id()), "FORBIDDEN");
        assertThat(service.get(alice, t.id()).passengers()).isNull();
        assertThat(service.get(driver, t.id()).passengers()).hasSize(1);
    }

    @Test
    void concurrentBookingsOfTheLastSeatCannotBothSucceed() throws Exception {
        AuthUser driver = auth(user("Driver"));
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        TripDto t = trip(driver, 1);

        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        for (AuthUser passenger : List.of(alice, bob)) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.book(passenger, t.id());
                    outcomes.add("OK");
                } catch (ApiException e) {
                    outcomes.add(e.getCode());
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertThat(outcomes).containsExactlyInAnyOrder("OK", "NO_SEAT_AVAILABLE");
    }
}
