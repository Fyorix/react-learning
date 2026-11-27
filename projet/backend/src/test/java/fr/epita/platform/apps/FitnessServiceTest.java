package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.fitness.dto.FitnessDtos.*;
import fr.epita.platform.apps.fitness.entity.FitnessBookingStatus;
import fr.epita.platform.apps.fitness.service.FitnessService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitnessServiceTest extends IntegrationTest {

    @Autowired
    FitnessService service;
    @Autowired
    AppRoles roles;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser coach;
    AuthUser alice;
    AuthUser bob;
    AuthUser chloe;
    Instant base;

    @BeforeEach
    void setUp() {
        coach = auth(user("Coralie Coach"));
        roles.grant(FitnessService.ROLES, Set.of(FitnessService.COACH), coach.id(), FitnessService.COACH);
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        chloe = auth(user("Chloé"));
        // le 10 du mois prochain à 10 h (heure de Paris) : tous les cours du test sont dans le même mois
        LocalDate d = LocalDate.now(ZoneId.of("Europe/Paris")).plusMonths(1).withDayOfMonth(10);
        base = d.atTime(10, 0).atZone(ZoneId.of("Europe/Paris")).toInstant();
    }

    private UUID cls(int hourOffset, int capacity) {
        return service.create(coach, new ClassRequest("Yoga", null, base.plus(hourOffset, ChronoUnit.HOURS), 45, capacity)).id();
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void fullClassWaitlistsAndFreedSeatPromotesAndDebitsTheNextOne() {
        UUID c = cls(0, 1);
        assertThat(service.book(alice, c).status()).isEqualTo(FitnessBookingStatus.CONFIRMED);
        BookingResult w = service.book(bob, c);
        assertThat(w.status()).isEqualTo(FitnessBookingStatus.WAITLISTED);
        assertThat(w.position()).isEqualTo(1);
        assertThat(w.creditsLeft()).isEqualTo(8);
        service.cancelBooking(alice, c);
        assertThat(service.get(bob, c).info().myStatus()).isEqualTo(FitnessBookingStatus.CONFIRMED);
        assertThat(service.me(alice).creditsLeft()).isEqualTo(8);
        assertCode(() -> service.book(bob, c), "ALREADY_BOOKED");
    }

    @Test
    void dailyLimitAndOverlap() {
        UUID a = cls(0, 10);
        UUID overlapping = cls(0, 10);
        UUID b = cls(3, 10);
        UUID c = cls(6, 10);
        service.book(alice, a);
        assertCode(() -> service.book(alice, overlapping), "OVERLAPPING_CLASS");
        service.book(alice, b);
        assertCode(() -> service.book(alice, c), "DAILY_LIMIT_REACHED");
        assertCode(() -> service.create(alice, new ClassRequest("Pilates", null, base, 45, 5)), "FORBIDDEN");
    }

    @Test
    void creditsRunOutAndLateCancellationLosesTheCredit() {
        // 8 cours sur 4 jours (2 par jour) consomment tous les crédits du mois du cours
        for (int day = 0; day < 4; day++) {
            service.book(alice, cls(day * 24, 10));
            service.book(alice, cls(day * 24 + 2, 10));
        }
        UUID ninth = cls(4 * 24, 10);
        assertCode(() -> service.book(alice, ninth), "NO_CREDIT_LEFT");
        // un cours plein ne débite pas : la liste d'attente reste possible sans crédit
        UUID full = cls(4 * 24 + 3, 1);
        service.book(bob, full);
        assertThat(service.book(alice, full).status()).isEqualTo(FitnessBookingStatus.WAITLISTED);
        UUID soon = cls(5 * 24, 10);
        jdbc.update("update fitness_classes set starts_at = now() + interval '1 hour' where id = ?", soon);
        service.book(chloe, soon);
        service.cancelBooking(chloe, soon);
        assertThat(service.me(chloe).bookings()).singleElement().satisfies(b -> {
            assertThat(b.status()).isEqualTo(FitnessBookingStatus.LATE_CANCELLED);
            assertThat(b.debited()).isTrue();
        });
    }

    @Test
    void cancelledClassRefundsEveryone() {
        UUID c = cls(0, 1);
        service.book(alice, c);
        service.book(bob, c);
        assertCode(() -> service.cancelClass(alice, c), "FORBIDDEN");
        service.cancelClass(coach, c);
        assertThat(service.me(alice).bookings().get(0).debited()).isFalse();
        assertThat(service.me(alice).bookings().get(0).status()).isEqualTo(FitnessBookingStatus.CANCELLED);
        assertCode(() -> service.book(chloe, c), "CLASS_CANCELLED");
    }
}
