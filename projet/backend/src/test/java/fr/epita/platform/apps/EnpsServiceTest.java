package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.enps.dto.EnpsDtos.*;
import fr.epita.platform.apps.enps.entity.EnpsQuestionType;
import fr.epita.platform.apps.enps.service.EnpsService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnpsServiceTest extends IntegrationTest {

    @Autowired
    EnpsService service;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser admin;
    List<AuthUser> support;
    List<AuthUser> dev;
    TeamDto supportTeam;
    TeamDto devTeam;
    SurveyDto survey;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        support = new ArrayList<>();
        dev = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            support.add(auth(user("Support " + i)));
        }
        for (int i = 0; i < 3; i++) {
            dev.add(auth(user("Dev " + i)));
        }
        supportTeam = service.createTeam(admin, new TeamRequest("Support"));
        devTeam = service.createTeam(admin, new TeamRequest("Dev"));
        service.setMembers(admin, supportTeam.id(), new MembersRequest(support.stream().map(AuthUser::id).toList()));
        service.setMembers(admin, devTeam.id(), new MembersRequest(dev.stream().map(AuthUser::id).toList()));
        survey = service.create(admin, new SurveyRequest("Baromètre", Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(2, ChronoUnit.DAYS),
                List.of(new QuestionRequest("Une idée ?", EnpsQuestionType.TEXT))));
    }

    private void answer(AuthUser who, int score, String text) {
        service.respond(who, survey.id(), new ResponseRequest(List.of(
                new AnswerRequest(survey.questions().get(0).id(), score, null),
                new AnswerRequest(survey.questions().get(1).id(), null, text))));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void enpsQuestionComesFirstAndAnswersAreOncePerPerson() {
        assertThat(survey.questions().get(0).enps()).isTrue();
        assertThat(survey.questions().get(0).label()).isEqualTo(EnpsService.ENPS_LABEL);
        answer(support.get(0), 9, "Top");
        assertCode(() -> answer(support.get(0), 3, "Finalement non"), "ALREADY_ANSWERED");
        assertThat(service.list(support.get(0)).get(0).answered()).isTrue();
        assertThatThrownBy(() -> service.respond(support.get(1), survey.id(), new ResponseRequest(List.of(
                new AnswerRequest(survey.questions().get(1).id(), null, "sans eNPS"))))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void storageKeepsNoLinkBetweenPeopleAndAnswers() {
        answer(support.get(0), 10, "Top");
        List<String> responseColumns = jdbc.queryForList("select column_name from information_schema.columns where table_name = 'enps_responses'", String.class);
        assertThat(responseColumns).containsExactlyInAnyOrder("id", "survey_id", "team_id");
        List<String> participationColumns = jdbc.queryForList("select column_name from information_schema.columns where table_name = 'enps_participations'", String.class);
        assertThat(participationColumns).containsExactlyInAnyOrder("survey_id", "user_id");
        assertThat(jdbc.queryForObject("select count(*) from enps_responses", Integer.class)).isEqualTo(1);
    }

    @Test
    void resultsNeedFiveResponsesPerScopeAndEnpsIsComputed() {
        int[] scores = {10, 9, 9, 7, 3, 0};
        for (int i = 0; i < 6; i++) {
            answer(support.get(i), scores[i], "Idée " + i);
        }
        for (int i = 0; i < 3; i++) {
            answer(dev.get(i), 10, null);
        }
        ResultsDto r = service.results(admin, survey.id(), null);
        // global : 9 réponses, promoteurs 6 (10,9,9,10,10,10), détracteurs 2 (3,0) → (6-2)/9 = 44 %
        assertThat(r.global().enps()).isEqualTo(44);
        ScopeResult s = r.teams().stream().filter(t -> t.teamName().equals("Support")).findFirst().orElseThrow();
        // support : promoteurs 3/6, détracteurs 2/6 → 17 %
        assertThat(s.enps()).isEqualTo(17);
        assertThat(s.texts().get(0).answers()).hasSize(6);
        ScopeResult d = r.teams().stream().filter(t -> t.teamName().equals("Dev")).findFirst().orElseThrow();
        assertThat(d.insufficientResponses()).isTrue();
        assertThat(d.enps()).isNull();
        assertCode(() -> service.results(support.get(0), survey.id(), null), "FORBIDDEN");
        assertThat(service.nonRespondents(admin, survey.id())).extracting(NonRespondentDto::name).containsExactly("Admin");
    }

    @Test
    void answersOnlyDuringTheOpeningWindow() {
        jdbc.update("update enps_surveys set closes_at = now() - interval '1 minute' where id = ?", survey.id());
        assertCode(() -> answer(support.get(0), 8, null), "SURVEY_CLOSED");
        assertThat(service.trends(support.get(0), null)).singleElement().satisfies(p -> assertThat(p.enps()).isNull());
        assertThat(service.results(support.get(0), survey.id(), null).global().insufficientResponses()).isTrue();
    }
}
