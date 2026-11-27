package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.AskRequest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.Offer;
import fr.epita.platform.apps.skills.dto.SkillsDtos.PairDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.ProfileRequest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.RequestDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.SessionRequest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.SkillDto;
import fr.epita.platform.apps.skills.service.SkillsService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillsServiceTest extends IntegrationTest {

    @Autowired
    SkillsService service;

    AuthUser admin;
    AuthUser mona;
    AuthUser max;
    AuthUser alice;
    AuthUser bob;
    SkillDto react;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        mona = auth(user("Mona Mentor"));
        max = auth(user("Max Mentor"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        react = service.addSkill(admin, "React");
        service.updateProfile(mona, new ProfileRequest(List.of(new Offer(react.id(), 4)), List.of(), 1));
        service.updateProfile(max, new ProfileRequest(List.of(new Offer(react.id(), 3)), List.of(), 2));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void suggestionsAreSortedByLevelAndExcludeMyself() {
        assertThat(service.suggestions(alice, react.id())).extracting("mentorName").containsExactly("Mona Mentor", "Max Mentor");
        assertThat(service.suggestions(mona, react.id())).extracting("mentorName").containsExactly("Max Mentor");
    }

    @Test
    void requestRules() {
        assertCode(() -> service.ask(mona, new AskRequest(mona.id(), react.id(), null)), "SELF_MENTORING");
        assertThatThrownBy(() -> service.ask(alice, new AskRequest(bob.id(), react.id(), null))).isInstanceOf(ValidationException.class);
        service.ask(alice, new AskRequest(mona.id(), react.id(), "Bonjour"));
        assertCode(() -> service.ask(alice, new AskRequest(max.id(), react.id(), null)), "ALREADY_PAIRED");
    }

    @Test
    void capacityIsEnforcedOnAcceptAndFullMentorsAreNotSuggested() {
        RequestDto a = service.ask(alice, new AskRequest(mona.id(), react.id(), null));
        RequestDto b = service.ask(bob, new AskRequest(mona.id(), react.id(), null));
        PairDto pair = service.accept(mona, a.id());
        assertThat(pair.myRole()).isEqualTo("MENTOR");
        assertCode(() -> service.accept(mona, b.id()), "MENTOR_FULL");
        assertThat(service.suggestions(bob, react.id())).extracting("mentorName").containsExactly("Max Mentor");
        assertCode(() -> service.accept(max, b.id()), "FORBIDDEN");
        assertCode(() -> service.ask(alice, new AskRequest(max.id(), react.id(), null)), "ALREADY_PAIRED");
        // terminer le binôme libère la place
        service.end(alice, pair.id());
        assertThat(service.accept(mona, b.id()).learnerName()).isEqualTo("Bob");
    }

    @Test
    void pendingRequestsExpireAfterFourteenDays() {
        RequestDto r = service.ask(alice, new AskRequest(mona.id(), react.id(), null));
        jdbc.update("update skills_requests set created_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(15))), r.id());
        assertThat(service.myRequests(alice, "sent").get(0).status()).isEqualTo("EXPIRED");
        assertCode(() -> service.accept(mona, r.id()), "INVALID_STATUS_TRANSITION");
        assertThat(service.ask(alice, new AskRequest(max.id(), react.id(), null)).status()).isEqualTo("PENDING");
    }

    @Test
    void sessionsAndProfileRules() {
        PairDto pair = service.accept(mona, service.ask(alice, new AskRequest(mona.id(), react.id(), null)).id());
        PairDto withSession = service.addSession(alice, pair.id(), new SessionRequest(LocalDate.now(), 60, "Hooks"));
        assertThat(withSession.totalMinutes()).isEqualTo(60);
        assertCode(() -> service.addSession(bob, pair.id(), new SessionRequest(LocalDate.now(), 30, null)), "FORBIDDEN");
        assertThatThrownBy(() -> service.updateProfile(mona, new ProfileRequest(List.of(), List.of(), 0)))
                .isInstanceOf(Exception.class);
        service.end(mona, pair.id());
        assertCode(() -> service.addSession(alice, pair.id(), new SessionRequest(LocalDate.now(), 30, null)), "PAIR_ENDED");
    }
}
