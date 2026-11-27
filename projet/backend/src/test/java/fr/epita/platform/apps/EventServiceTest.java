package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.events.dto.EventDtos.EventDto;
import fr.epita.platform.apps.events.dto.EventDtos.EventRequest;
import fr.epita.platform.apps.events.entity.RegistrationStatus;
import fr.epita.platform.apps.events.service.EventService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventServiceTest extends IntegrationTest {

    @Autowired
    EventService service;

    private EventDto event(AuthUser organizer, int capacity) {
        Instant starts = Instant.now().plus(10, ChronoUnit.DAYS);
        return service.create(organizer, new EventRequest("Afterwork", "Bowling", "Paris", starts, capacity,
                starts.minus(1, ChronoUnit.DAYS)));
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void fullEventPutsPeopleOnTheWaitlistAndPromotesThem() {
        AuthUser org = auth(user("Organisateur"));
        AuthUser a = auth(user("Anna"));
        AuthUser b = auth(user("Bruno"));
        AuthUser c = auth(user("Chloe"));
        EventDto e = event(org, 1);

        assertThat(service.register(a, e.id()).status()).isEqualTo(RegistrationStatus.CONFIRMED);
        var rb = service.register(b, e.id());
        assertThat(rb.status()).isEqualTo(RegistrationStatus.WAITLISTED);
        assertThat(rb.position()).isEqualTo(1);
        assertThat(service.register(c, e.id()).position()).isEqualTo(2);

        service.unregister(a, e.id());
        assertThat(service.get(b, e.id()).myStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(service.get(c, e.id()).myPosition()).isEqualTo(1);
    }

    @Test
    void organizerAndDuplicatesAreRefused() {
        AuthUser org = auth(user("Organisateur"));
        AuthUser a = auth(user("Anna"));
        EventDto e = event(org, 5);
        assertCode(() -> service.register(org, e.id()), "ORGANIZER_ALREADY_IN");
        service.register(a, e.id());
        assertCode(() -> service.register(a, e.id()), "ALREADY_REGISTERED");
    }

    @Test
    void registrationsCloseAtTheDeadline() {
        AuthUser org = auth(user("Organisateur"));
        AuthUser a = auth(user("Anna"));
        EventDto e = event(org, 5);
        service.register(a, e.id());
        jdbc.update("update events set registration_deadline = now() - interval '1 minute' where id = ?", e.id());
        assertCode(() -> service.register(auth(user("Bruno")), e.id()), "REGISTRATION_CLOSED");
        assertCode(() -> service.unregister(a, e.id()), "REGISTRATION_CLOSED");
    }

    @Test
    void capacityCanGrowButNotBelowConfirmed() {
        AuthUser org = auth(user("Organisateur"));
        AuthUser a = auth(user("Anna"));
        AuthUser b = auth(user("Bruno"));
        EventDto e = event(org, 1);
        service.register(a, e.id());
        service.register(b, e.id());

        EventRequest base = new EventRequest(e.title(), e.description(), e.location(), e.startsAt(), 0,
                e.registrationDeadline());
        assertCode(() -> service.update(org, e.id(), withCapacity(base, 0)), "CAPACITY_BELOW_CONFIRMED");
        service.update(org, e.id(), withCapacity(base, 2));
        assertThat(service.get(b, e.id()).myStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void cancelledEventRefusesRegistrations() {
        AuthUser org = auth(user("Organisateur"));
        EventDto e = event(org, 5);
        assertCode(() -> service.cancel(auth(user("Anna")), e.id()), "FORBIDDEN");
        service.cancel(org, e.id());
        assertCode(() -> service.register(auth(user("Bruno")), e.id()), "EVENT_CANCELLED");
    }

    private static EventRequest withCapacity(EventRequest r, int capacity) {
        return new EventRequest(r.title(), r.description(), r.location(), r.startsAt(), capacity,
                r.registrationDeadline());
    }
}
