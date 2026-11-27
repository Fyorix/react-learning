package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.meeting.dto.MeetingDtos.*;
import fr.epita.platform.apps.meeting.entity.Availability;
import fr.epita.platform.apps.meeting.entity.PollStatus;
import fr.epita.platform.apps.meeting.service.MeetingService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingServiceTest extends IntegrationTest {

    @Autowired
    MeetingService service;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser org;
    AuthUser alice;
    AuthUser bob;
    AuthUser eve;
    Instant base;

    @BeforeEach
    void setUp() {
        org = auth(user("Organisatrice"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        eve = auth(user("Ève"));
        base = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    private PollDto poll(int slotCount) {
        List<SlotInput> s = java.util.stream.IntStream.range(0, slotCount).mapToObj(i -> new SlotInput(base.plus(i, ChronoUnit.HOURS))).toList();
        return service.create(org, new PollRequest("Point projet", 60, "Salle Turing", List.of(alice.id(), bob.id()), s));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void slotsMustBeFutureAndDistinct() {
        assertThatThrownBy(() -> service.create(org, new PollRequest("x", 30, null, List.of(alice.id()),
                List.of(new SlotInput(base), new SlotInput(base))))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(org, new PollRequest("x", 30, null, List.of(alice.id()),
                List.of(new SlotInput(base), new SlotInput(Instant.now().minusSeconds(60)))))).isInstanceOf(ValidationException.class);
        PollDto p = poll(3);
        assertThat(p.participants()).hasSize(3);
        assertThat(p.slots()).hasSize(3);
    }

    @Test
    void suggestionPrefersYesThenIfNeededThenEarliest() {
        PollDto p = poll(3);
        var s0 = p.slots().get(0).id();
        var s1 = p.slots().get(1).id();
        var s2 = p.slots().get(2).id();
        assertThat(p.suggestedSlotId()).isEqualTo(s0);
        service.answer(alice, p.id(), List.of(new AnswerInput(s0, Availability.NO), new AnswerInput(s1, Availability.YES), new AnswerInput(s2, Availability.YES)));
        service.answer(bob, p.id(), List.of(new AnswerInput(s1, Availability.IF_NEEDED), new AnswerInput(s2, Availability.YES)));
        assertThat(service.get(org, p.id()).suggestedSlotId()).isEqualTo(s2);
        service.answer(bob, p.id(), List.of(new AnswerInput(s1, Availability.YES), new AnswerInput(s2, Availability.YES)));
        assertThat(service.get(org, p.id()).suggestedSlotId()).isEqualTo(s1);
        assertCode(() -> service.answer(eve, p.id(), List.of(new AnswerInput(s1, Availability.YES))), "FORBIDDEN");
        assertCode(() -> service.update(org, p.id(), new PollRequest("x", 30, null, List.of(alice.id()), List.of(new SlotInput(base), new SlotInput(base.plusSeconds(3600))))), "POLL_HAS_ANSWERS");
    }

    @Test
    void decisionFreezesAnswersAndPastSlotsCannotBeChosen() {
        PollDto p = poll(2);
        var s0 = p.slots().get(0).id();
        var s1 = p.slots().get(1).id();
        assertCode(() -> service.decide(alice, p.id(), s0), "FORBIDDEN");
        jdbc.update("update meeting_slots set start_at = now() - interval '1 hour' where id = ?", s0);
        assertCode(() -> service.decide(org, p.id(), s0), "SLOT_IN_PAST");
        PollDto decided = service.decide(org, p.id(), s1);
        assertThat(decided.status()).isEqualTo(PollStatus.DECIDED);
        assertThat(decided.decidedSlotId()).isEqualTo(s1);
        assertCode(() -> service.answer(alice, p.id(), List.of(new AnswerInput(s1, Availability.YES))), "POLL_DECIDED");
        assertThat(service.list(alice, "participant", PollStatus.DECIDED)).hasSize(1);
        assertThat(service.list(org, "organizer", null).get(0).decidedStart()).isNotNull();
    }

    @Test
    void remindersAreLimitedToOncePerDay() {
        PollDto p = poll(2);
        service.answer(alice, p.id(), List.of(new AnswerInput(p.slots().get(0).id(), Availability.YES)));
        assertThat(service.remind(org, p.id()).reminded()).containsExactlyInAnyOrder("Bob", "Organisatrice");
        assertCode(() -> service.remind(org, p.id()), "REMINDER_TOO_SOON");
        jdbc.update("update meeting_polls set last_reminder_at = now() - interval '25 hours' where id = ?", p.id());
        assertThat(service.remind(org, p.id()).reminded()).hasSize(2);
    }
}
