package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.appraisal.dto.AppraisalDtos.*;
import fr.epita.platform.apps.appraisal.entity.QuestionType;
import fr.epita.platform.apps.appraisal.entity.Step;
import fr.epita.platform.apps.appraisal.service.AppraisalService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppraisalServiceTest extends IntegrationTest {

    @Autowired
    AppraisalService service;
    @Autowired
    ManagerRelations relations;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser admin;
    AuthUser manager;
    AuthUser alice;
    AuthUser loner;
    CampaignDto campaign;
    UUID textQ;
    UUID ratingQ;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        manager = auth(user("Maya Manager"));
        alice = auth(user("Alice"));
        loner = auth(user("Sans manager"));
        relations.assign(AppraisalService.REPORTS, alice.id(), manager.id());
        campaign = service.launch(admin, new CampaignRequest(LocalDate.now().getYear(), LocalDate.now().plusDays(20), List.of(
                new QuestionRequest("Réussites de l'année ?", QuestionType.TEXT),
                new QuestionRequest("Satisfaction globale", QuestionType.RATING_1_5))));
        textQ = campaign.template().get(0).id();
        ratingQ = campaign.template().get(1).id();
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    private List<AnswerRequest> answers() {
        return List.of(new AnswerRequest(textQ, "Migration React", null), new AnswerRequest(ratingQ, null, 4));
    }

    @Test
    void campaignCreatesOneAppraisalPerManagedEmployeeOncePerYear() {
        assertThat(campaign.appraisals()).isEqualTo(1);
        assertCode(() -> service.mine(loner), "NOT_FOUND");
        assertCode(() -> service.launch(admin, new CampaignRequest(campaign.year(), LocalDate.now().plusDays(5), List.of(new QuestionRequest("Q", QuestionType.TEXT)))), "CAMPAIGN_EXISTS");
    }

    @Test
    void fullFlowWithStepsVisibilityAndSignatures() {
        AppraisalDto a = service.mine(alice);
        service.saveSelfReview(alice, a.id(), new SelfReviewRequest(List.of(new AnswerRequest(textQ, "Brouillon", null))));
        // le manager ne voit rien avant la soumission
        assertThat(service.get(manager, a.id()).selfAnswers()).isNull();
        assertCode(() -> service.selfAnswers(manager, a.id()), "NOT_FOUND");
        assertThatThrownBy(() -> service.submitSelfReview(alice, a.id())).isInstanceOf(ValidationException.class);
        assertCode(() -> service.saveManagerReview(manager, a.id(), new ManagerReviewRequest(answers(), List.of())), "INVALID_STEP");
        service.saveSelfReview(alice, a.id(), new SelfReviewRequest(answers()));
        service.submitSelfReview(alice, a.id());
        assertThat(service.selfAnswers(manager, a.id())).hasSize(2);
        assertCode(() -> service.saveSelfReview(alice, a.id(), new SelfReviewRequest(answers())), "INVALID_STEP");
        service.saveManagerReview(manager, a.id(), new ManagerReviewRequest(answers(), List.of()));
        assertThatThrownBy(() -> service.submitManagerReview(manager, a.id())).isInstanceOf(ValidationException.class);
        service.saveManagerReview(manager, a.id(), new ManagerReviewRequest(answers(), List.of(new ObjectiveRequest("Mentorer un junior", null))));
        service.submitManagerReview(manager, a.id());
        assertThat(service.get(alice, a.id()).objectives()).hasSize(1);
        assertCode(() -> service.sign(manager, a.id(), null), "INVALID_STEP");
        service.sign(alice, a.id(), "Merci");
        AppraisalDto done = service.sign(manager, a.id(), null);
        assertThat(done.step()).isEqualTo(Step.COMPLETED);
        assertThat(done.finalComment()).isEqualTo("Merci");
        assertCode(() -> service.reopen(admin, a.id(), null), "INVALID_STEP");
        assertThat(service.progress(admin, campaign.id()).byStep().get(Step.COMPLETED)).isEqualTo(1);
    }

    @Test
    void ratingsAreValidatedByQuestionType() {
        AppraisalDto a = service.mine(alice);
        assertThatThrownBy(() -> service.saveSelfReview(alice, a.id(), new SelfReviewRequest(List.of(new AnswerRequest(ratingQ, "texte", null)))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.saveSelfReview(alice, a.id(), new SelfReviewRequest(List.of(new AnswerRequest(textQ, null, 3)))))
                .isInstanceOf(ValidationException.class);
        assertCode(() -> service.saveSelfReview(manager, a.id(), new SelfReviewRequest(answers())), "FORBIDDEN");
    }

    @Test
    void afterDeadlineOnlyAReopenAllowsInput() {
        AppraisalDto a = service.mine(alice);
        jdbc.update("update appraisal_campaigns set deadline = current_date - 2 where id = ?", campaign.id());
        assertCode(() -> service.saveSelfReview(alice, a.id(), new SelfReviewRequest(answers())), "DEADLINE_PASSED");
        assertThat(service.progress(admin, campaign.id()).late()).isEqualTo(1);
        service.reopen(admin, a.id(), 3);
        assertThat(service.saveSelfReview(alice, a.id(), new SelfReviewRequest(answers())).editable()).isTrue();
    }
}
