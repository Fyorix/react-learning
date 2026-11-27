package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.recruitment.dto.RecruitmentDtos.*;
import fr.epita.platform.apps.recruitment.entity.JobStatus;
import fr.epita.platform.apps.recruitment.entity.Recommendation;
import fr.epita.platform.apps.recruitment.entity.Stage;
import fr.epita.platform.apps.recruitment.service.RecruitmentService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecruitmentServiceTest extends IntegrationTest {

    @Autowired
    RecruitmentService service;
    @Autowired
    AppRoles roles;

    AuthUser recruiter;
    AuthUser i1;
    AuthUser i2;
    AuthUser outsider;
    JobDto job;

    static final MockMultipartFile CV = new MockMultipartFile("cv", "cv.pdf", "application/pdf", "%PDF-1.4 test".getBytes());

    @BeforeEach
    void setUp() {
        recruiter = auth(user("Rita Recruteuse"));
        roles.grant(RecruitmentService.ROLES, Set.of(RecruitmentService.RECRUITER), recruiter.id(), RecruitmentService.RECRUITER);
        i1 = auth(user("Ivan Intervenant"));
        i2 = auth(user("Inès Intervenante"));
        outsider = auth(user("Oscar"));
        job = service.createJob(recruiter, new JobRequest("Dev React", "Produit", "Front", "Paris", null));
    }

    private ApplicationDto candidate(String email) {
        return service.apply(recruiter, job.id(), new CandidateRequest("Léa", "Moreau", email, null), CV);
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void onlyRecruitersManageJobsAndApplicationsAreUniquePerEmail() {
        assertCode(() -> service.createJob(outsider, new JobRequest("X", "Y", "Z", "W", null)), "FORBIDDEN");
        candidate("lea@example.com");
        assertCode(() -> candidate("LEA@example.com"), "ALREADY_APPLIED");
        service.updateJob(recruiter, job.id(), new JobRequest("Dev React", "Produit", "Front", "Paris", JobStatus.ON_HOLD));
        assertCode(() -> candidate("autre@example.com"), "JOB_NOT_OPEN");
        assertCode(() -> service.get(outsider, candidate2Id()), "NOT_FOUND");
    }

    private java.util.UUID candidate2Id() {
        return service.pipeline(recruiter, job.id()).columns().get(0).applications().get(0).id();
    }

    @Test
    void stagesMoveForwardOnlyAndSkippingNeedsAReason() {
        ApplicationDto a = candidate("lea@example.com");
        assertThatThrownBy(() -> service.move(recruiter, a.id(), new MoveRequest(Stage.TECH_INTERVIEW, null, null))).isInstanceOf(ValidationException.class);
        service.move(recruiter, a.id(), new MoveRequest(Stage.TECH_INTERVIEW, "Entretien RH fait par le manager", null));
        assertCode(() -> service.move(recruiter, a.id(), new MoveRequest(Stage.HR_INTERVIEW, null, null)), "INVALID_STAGE_TRANSITION");
        assertThatThrownBy(() -> service.move(recruiter, a.id(), new MoveRequest(Stage.REJECTED, " ", null))).isInstanceOf(ValidationException.class);
        ApplicationDto rejected = service.move(recruiter, a.id(), new MoveRequest(Stage.REJECTED, "Prétentions trop élevées", null));
        assertThat(rejected.history()).extracting(StageEventDto::toStage).containsExactly(Stage.SCREENING, Stage.TECH_INTERVIEW, Stage.REJECTED);
        assertCode(() -> service.move(recruiter, a.id(), new MoveRequest(Stage.OFFER, "x", null)), "INVALID_STAGE_TRANSITION");
    }

    @Test
    void offerNeedsTwoYesAndHiringFillsTheJob() {
        ApplicationDto a = candidate("lea@example.com");
        service.move(recruiter, a.id(), new MoveRequest(Stage.HR_INTERVIEW, null, List.of(i1.id(), i2.id())));
        assertThat(service.myTodo(i1)).hasSize(1);
        assertCode(() -> service.evaluate(outsider, a.id(), new EvaluationRequest(Stage.HR_INTERVIEW, 3, Recommendation.YES, "ok")), "NOT_FOUND");
        service.evaluate(i1, a.id(), new EvaluationRequest(Stage.HR_INTERVIEW, 4, Recommendation.YES, "Très bon"));
        assertCode(() -> service.evaluate(i1, a.id(), new EvaluationRequest(Stage.HR_INTERVIEW, 4, Recommendation.YES, "encore")), "ALREADY_EVALUATED");
        assertThat(service.myTodo(i1)).isEmpty();
        service.move(recruiter, a.id(), new MoveRequest(Stage.TECH_INTERVIEW, null, null));
        assertCode(() -> service.move(recruiter, a.id(), new MoveRequest(Stage.OFFER, null, null)), "NOT_ENOUGH_APPROVALS");
        assertCode(() -> service.evaluate(i2, a.id(), new EvaluationRequest(Stage.HR_INTERVIEW, 3, Recommendation.YES, "trop tard")), "INVALID_STAGE_TRANSITION");
        service.evaluate(i2, a.id(), new EvaluationRequest(Stage.TECH_INTERVIEW, 3, Recommendation.YES, "Solide"));
        service.move(recruiter, a.id(), new MoveRequest(Stage.OFFER, null, null));
        ApplicationDto hired = service.move(recruiter, a.id(), new MoveRequest(Stage.HIRED, null, null));
        assertThat(hired.stage()).isEqualTo(Stage.HIRED);
        assertThat(service.jobs(JobStatus.FILLED)).extracting(JobDto::id).containsExactly(job.id());
        StatsDto stats = service.stats(recruiter);
        assertThat(stats.hires()).isEqualTo(1);
        assertThat(stats.passRate().get(Stage.SCREENING)).isEqualTo(100);
        assertThat(service.cv(i1, a.id()).content()).startsWith("%PDF".getBytes());
    }
}
