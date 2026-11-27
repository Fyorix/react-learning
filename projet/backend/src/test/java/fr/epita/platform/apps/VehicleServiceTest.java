package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.BookingDto;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.BookingRequest;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.CheckinRequest;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.IssueRequest;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.VehicleDto;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.VehicleRequest;
import fr.epita.platform.apps.vehicle.entity.VehicleEnums.BookingStatus;
import fr.epita.platform.apps.vehicle.entity.VehicleEnums.Severity;
import fr.epita.platform.apps.vehicle.entity.VehicleEnums.VehicleStatus;
import fr.epita.platform.apps.vehicle.service.VehicleService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleServiceTest extends IntegrationTest {

    @Autowired
    VehicleService service;

    AuthUser admin;
    AuthUser alice;
    AuthUser bob;
    VehicleDto zoe;
    Instant d1 = Instant.now().plus(Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.HOURS);

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        zoe = service.create(admin, new VehicleRequest("GA-123-BC", "Renault Zoé", 5, "B", null, 14_900));
    }

    private BookingRequest slot(int fromHours, int toHours) {
        return new BookingRequest(d1.plus(Duration.ofHours(fromHours)), d1.plus(Duration.ofHours(toHours)), "Client", "Lyon");
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void overlappingBookingsAndDurationRules() {
        service.book(alice, zoe.id(), slot(0, 8));
        assertCode(() -> service.book(bob, zoe.id(), slot(4, 10)), "VEHICLE_ALREADY_BOOKED");
        service.book(bob, zoe.id(), slot(8, 12));
        assertThatThrownBy(() -> service.book(bob, zoe.id(), slot(24, 24 * 7))).isInstanceOf(ValidationException.class);
        assertThat(service.list(d1, d1.plus(Duration.ofHours(1)))).isEmpty();
        assertThat(service.list(d1.plus(Duration.ofHours(13)), d1.plus(Duration.ofHours(14)))).hasSize(1);
    }

    @Test
    void mileageIsCheckedAtCheckoutAndCheckin() {
        BookingDto b = service.book(alice, zoe.id(), slot(0, 8));
        assertThatThrownBy(() -> service.checkout(alice, b.id(), 14_000)).isInstanceOf(ValidationException.class);
        assertCode(() -> service.checkout(bob, b.id(), 15_000), "FORBIDDEN");
        service.checkout(alice, b.id(), 14_950);
        assertThatThrownBy(() -> service.checkin(alice, b.id(), new CheckinRequest(14_900, null))).isInstanceOf(ValidationException.class);
        BookingDto back = service.checkin(alice, b.id(), new CheckinRequest(15_200, null));
        assertThat(back.status()).isEqualTo(BookingStatus.RETURNED);
        assertThat(service.list(null, null).get(0).mileage()).isEqualTo(15_200);
        assertThat(service.maintenance(admin).serviceDue()).isEmpty();
        // dernier entretien à 0 km : 15 200 km parcourus depuis, entretien à prévoir
        jdbc.update("update vehicles set last_service_mileage = 0 where id = ?", zoe.id());
        assertThat(service.maintenance(admin).serviceDue()).hasSize(1);
        assertThat(service.service(admin, zoe.id()).kmSinceService()).isZero();
    }

    @Test
    void blockingIssueSendsTheVehicleToMaintenanceAndCancelsFutureBookings() {
        BookingDto now = service.book(alice, zoe.id(), slot(0, 4));
        BookingDto later = service.book(bob, zoe.id(), slot(48, 52));
        service.checkout(alice, now.id(), 15_000);
        service.checkin(alice, now.id(), new CheckinRequest(15_100, new IssueRequest(Severity.BLOCKING, "Frein qui grince")));
        assertThat(service.list(null, null).get(0).status()).isEqualTo(VehicleStatus.MAINTENANCE);
        assertThat(service.mine(bob)).filteredOn(b -> b.id().equals(later.id())).extracting(BookingDto::status).containsExactly(BookingStatus.CANCELLED);
        assertCode(() -> service.book(bob, zoe.id(), slot(60, 62)), "VEHICLE_UNAVAILABLE");
        assertThat(service.maintenance(admin).openIssues()).hasSize(1);
        service.service(admin, zoe.id());
        assertThat(service.book(bob, zoe.id(), slot(60, 62)).status()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void cancellationBeforeDepartureOnly() {
        BookingDto b = service.book(alice, zoe.id(), slot(0, 4));
        service.checkout(alice, b.id(), 15_000);
        assertCode(() -> service.cancel(alice, b.id()), "INVALID_STATUS_TRANSITION");
        BookingDto c = service.book(alice, zoe.id(), slot(10, 12));
        assertCode(() -> service.cancel(bob, c.id()), "FORBIDDEN");
        service.cancel(admin, c.id());
    }
}
