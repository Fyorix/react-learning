package fr.epita.platform.apps.appraisal.service;

import fr.epita.platform.apps.appraisal.dto.AppraisalDtos.*;
import fr.epita.platform.apps.appraisal.entity.*;
import fr.epita.platform.apps.appraisal.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import fr.epita.platform.common.relation.ReportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Étapes imposées : SELF_REVIEW → MANAGER_REVIEW → EMPLOYEE_SIGNATURE → MANAGER_SIGNATURE → COMPLETED.
 * Saisie possible jusqu'à la date limite incluse (heure de Paris) ou pendant une réouverture accordée par un ADMIN.
 */
@Service
@RequiredArgsConstructor
public class AppraisalService {

    public static final String REPORTS = "appraisal_reports";
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final AppraisalCampaignRepository campaigns;
    private final AppraisalQuestionRepository questions;
    private final AppraisalRepository appraisals;
    private final AppraisalAnswerRepository answers;
    private final AppraisalObjectiveRepository objectives;
    private final ManagerRelations relations;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- campagnes ----------

    @Transactional
    public CampaignDto launch(AuthUser me, CampaignRequest req) {
        me.requireAdmin();
        if (campaigns.existsByYear(req.year())) {
            throw ApiException.conflict("CAMPAIGN_EXISTS", "Une campagne existe déjà pour " + req.year());
        }
        if (!req.deadline().isAfter(LocalDate.now(clock.withZone(ZONE)))) {
            throw ValidationException.field("deadline", "doit être dans le futur");
        }
        AppraisalCampaign c = new AppraisalCampaign();
        c.setYear(req.year());
        c.setDeadline(req.deadline());
        campaigns.saveAndFlush(c);
        for (int i = 0; i < req.template().size(); i++) {
            AppraisalQuestion q = new AppraisalQuestion();
            q.setCampaignId(c.getId());
            q.setPosition(i);
            q.setQuestion(req.template().get(i).question().trim());
            q.setType(req.template().get(i).type());
            questions.save(q);
        }
        // un entretien par salarié ayant un manager, manager figé à cette date
        for (ReportDto r : relations.list(REPORTS)) {
            Appraisal a = new Appraisal();
            a.setCampaignId(c.getId());
            a.setEmployeeId(r.employeeId());
            a.setManagerId(r.managerId());
            a.setStep(Step.SELF_REVIEW);
            appraisals.save(a);
        }
        appraisals.flush();
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public List<CampaignDto> campaigns(AuthUser me) {
        me.requireAdmin();
        return campaigns.findAllByOrderByYearDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProgressDto progress(AuthUser me, UUID id) {
        me.requireAdmin();
        AppraisalCampaign c = campaigns.findById(id).orElseThrow(() -> ApiException.notFound("Campagne"));
        List<Appraisal> list = appraisals.findByCampaignId(id);
        Map<Step, Long> byStep = new EnumMap<>(Step.class);
        for (Step s : Step.values()) {
            byStep.put(s, list.stream().filter(a -> a.getStep() == s).count());
        }
        long late = deadlinePassed(c) ? list.stream().filter(a -> a.getStep() != Step.COMPLETED).count() : 0;
        return new ProgressDto(c.getId(), c.getYear(), c.getDeadline(), list.size(), byStep, late);
    }

    @Transactional(readOnly = true)
    public List<AppraisalSummary> campaignAppraisals(AuthUser me, UUID id) {
        me.requireAdmin();
        AppraisalCampaign c = campaigns.findById(id).orElseThrow(() -> ApiException.notFound("Campagne"));
        List<Appraisal> list = appraisals.findByCampaignId(id);
        Map<UUID, String> names = userLookup.namesOf(list, Appraisal::getEmployeeId);
        return list.stream().map(a -> new AppraisalSummary(a.getId(), c.getYear(), a.getEmployeeId(), names.get(a.getEmployeeId()), a.getStep(),
                a.getStep() != Step.COMPLETED && deadlinePassed(c)))
                .sorted(Comparator.comparing(AppraisalSummary::employeeName, Comparator.nullsLast(String::compareTo))).toList();
    }

    // ---------- consultation ----------

    @Transactional(readOnly = true)
    public AppraisalDto mine(AuthUser me) {
        Appraisal a = appraisals.findByEmployeeIdOrderByCreatedAtDesc(me.id()).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Entretien"));
        return detail(me, a);
    }

    @Transactional(readOnly = true)
    public List<AppraisalSummary> team(AuthUser me) {
        Map<UUID, AppraisalCampaign> cs = campaigns.findAll().stream().collect(Collectors.toMap(AppraisalCampaign::getId, Function.identity()));
        List<Appraisal> list = appraisals.findByManagerIdOrderByCreatedAtDesc(me.id());
        Map<UUID, String> names = userLookup.namesOf(list, Appraisal::getEmployeeId);
        return list.stream().map(a -> {
            AppraisalCampaign c = cs.get(a.getCampaignId());
            return new AppraisalSummary(a.getId(), c.getYear(), a.getEmployeeId(), names.get(a.getEmployeeId()), a.getStep(),
                    a.getStep() != Step.COMPLETED && deadlinePassed(c));
        }).sorted(Comparator.comparing(AppraisalSummary::year).reversed().thenComparing(AppraisalSummary::employeeName, Comparator.nullsLast(String::compareTo))).toList();
    }

    @Transactional(readOnly = true)
    public AppraisalDto get(AuthUser me, UUID id) {
        return detail(me, visible(me, id));
    }

    /** Réponses de l'auto-évaluation : pour le manager, 404 tant qu'elle n'est pas soumise. */
    @Transactional(readOnly = true)
    public List<AnswerDto> selfAnswers(AuthUser me, UUID id) {
        Appraisal a = visible(me, id);
        if (!a.getEmployeeId().equals(me.id()) && a.getSelfSubmittedAt() == null) {
            throw ApiException.notFound("Auto-évaluation");
        }
        return answersOf(a, Author.SELF);
    }

    // ---------- saisie ----------

    @Transactional
    public AppraisalDto saveSelfReview(AuthUser me, UUID id, SelfReviewRequest req) {
        Appraisal a = writable(me, id, Step.SELF_REVIEW, true);
        saveAnswers(a, Author.SELF, req.answers());
        return detail(me, a);
    }

    @Transactional
    public AppraisalDto submitSelfReview(AuthUser me, UUID id) {
        Appraisal a = writable(me, id, Step.SELF_REVIEW, true);
        requireComplete(a, Author.SELF);
        a.setSelfSubmittedAt(clock.instant());
        a.setStep(Step.MANAGER_REVIEW);
        return detail(me, appraisals.saveAndFlush(a));
    }

    @Transactional
    public AppraisalDto saveManagerReview(AuthUser me, UUID id, ManagerReviewRequest req) {
        Appraisal a = writable(me, id, Step.MANAGER_REVIEW, false);
        saveAnswers(a, Author.MANAGER, req.answers());
        objectives.deleteAll(objectives.findByAppraisalIdOrderByPositionAsc(id));
        objectives.flush();
        for (int i = 0; i < req.objectives().size(); i++) {
            AppraisalObjective o = new AppraisalObjective();
            o.setAppraisalId(id);
            o.setPosition(i);
            o.setTitle(req.objectives().get(i).title().trim());
            String d = req.objectives().get(i).description();
            o.setDescription(d == null || d.isBlank() ? null : d.trim());
            objectives.save(o);
        }
        objectives.flush();
        return detail(me, a);
    }

    @Transactional
    public AppraisalDto submitManagerReview(AuthUser me, UUID id) {
        Appraisal a = writable(me, id, Step.MANAGER_REVIEW, false);
        requireComplete(a, Author.MANAGER);
        int n = objectives.findByAppraisalIdOrderByPositionAsc(id).size();
        if (n < 1 || n > 5) {
            throw ValidationException.field("objectives", "1 à 5 objectifs sont requis");
        }
        a.setManagerSubmittedAt(clock.instant());
        a.setStep(Step.EMPLOYEE_SIGNATURE);
        return detail(me, appraisals.saveAndFlush(a));
    }

    /** Le salarié signe (avec un commentaire final facultatif), puis le manager : l'entretien est figé. */
    @Transactional
    public AppraisalDto sign(AuthUser me, UUID id, String finalComment) {
        Appraisal a = appraisals.lock(id).orElseThrow(() -> ApiException.notFound("Entretien"));
        visibleOrThrow(me, a);
        boolean employee = a.getEmployeeId().equals(me.id());
        boolean manager = a.getManagerId().equals(me.id());
        if (employee && a.getStep() == Step.EMPLOYEE_SIGNATURE) {
            requireOpen(a);
            a.setFinalComment(finalComment == null || finalComment.isBlank() ? null : finalComment.trim());
            a.setEmployeeSignedAt(clock.instant());
            a.setStep(Step.MANAGER_SIGNATURE);
        } else if (manager && a.getStep() == Step.MANAGER_SIGNATURE) {
            requireOpen(a);
            a.setManagerSignedAt(clock.instant());
            a.setStep(Step.COMPLETED);
        } else if (!employee && !manager) {
            throw ApiException.forbidden("Seuls le salarié et son manager signent");
        } else {
            throw ApiException.conflict("INVALID_STEP", "Signature impossible à l'étape " + a.getStep());
        }
        return detail(me, appraisals.saveAndFlush(a));
    }

    @Transactional
    public AppraisalDto reopen(AuthUser me, UUID id, Integer days) {
        me.requireAdmin();
        Appraisal a = appraisals.lock(id).orElseThrow(() -> ApiException.notFound("Entretien"));
        if (a.getStep() == Step.COMPLETED) {
            throw ApiException.conflict("INVALID_STEP", "Un entretien signé par les deux n'est plus modifiable");
        }
        a.setReopenedUntil(clock.instant().plus(Duration.ofDays(days == null ? 7 : days)));
        return detail(me, appraisals.saveAndFlush(a));
    }

    // ---------- règles ----------

    private Appraisal writable(AuthUser me, UUID id, Step step, boolean employee) {
        Appraisal a = appraisals.lock(id).orElseThrow(() -> ApiException.notFound("Entretien"));
        visibleOrThrow(me, a);
        UUID actor = employee ? a.getEmployeeId() : a.getManagerId();
        if (!actor.equals(me.id())) {
            throw ApiException.forbidden(employee ? "Réservé au salarié" : "Réservé au manager");
        }
        if (a.getStep() != step) {
            throw ApiException.conflict("INVALID_STEP", "Action impossible à l'étape " + a.getStep());
        }
        requireOpen(a);
        return a;
    }

    private void requireOpen(Appraisal a) {
        if (!open(a)) {
            throw ApiException.conflict("DEADLINE_PASSED", "La date limite est passée : demandez une réouverture à un administrateur");
        }
    }

    private boolean open(Appraisal a) {
        AppraisalCampaign c = campaigns.findById(a.getCampaignId()).orElseThrow();
        return !deadlinePassed(c) || (a.getReopenedUntil() != null && clock.instant().isBefore(a.getReopenedUntil()));
    }

    private boolean deadlinePassed(AppraisalCampaign c) {
        Instant end = c.getDeadline().plusDays(1).atStartOfDay(ZONE).toInstant();
        return !clock.instant().isBefore(end);
    }

    private void saveAnswers(Appraisal a, Author author, List<AnswerRequest> reqs) {
        Map<UUID, AppraisalQuestion> qs = questions.findByCampaignIdOrderByPositionAsc(a.getCampaignId()).stream()
                .collect(Collectors.toMap(AppraisalQuestion::getId, Function.identity()));
        var errors = new ValidationException.Collector();
        for (int i = 0; i < reqs.size(); i++) {
            AnswerRequest r = reqs.get(i);
            AppraisalQuestion q = qs.get(r.questionId());
            errors.check(q != null, "answers[" + i + "].questionId", "question inconnue pour cette campagne");
            if (q != null && q.getType() == QuestionType.RATING_1_5) {
                errors.check(r.text() == null || r.text().isBlank(), "answers[" + i + "].text", "attendu : une note (rating)");
            }
            if (q != null && q.getType() == QuestionType.TEXT) {
                errors.check(r.rating() == null, "answers[" + i + "].rating", "attendu : un texte (text)");
            }
        }
        errors.throwIfAny();
        Map<UUID, AppraisalAnswer> existing = answers.findByAppraisalIdAndAuthor(a.getId(), author).stream()
                .collect(Collectors.toMap(AppraisalAnswer::getQuestionId, Function.identity()));
        for (AnswerRequest r : reqs) {
            AppraisalAnswer ans = existing.getOrDefault(r.questionId(), new AppraisalAnswer());
            ans.setAppraisalId(a.getId());
            ans.setQuestionId(r.questionId());
            ans.setAuthor(author);
            ans.setTextValue(r.text() == null || r.text().isBlank() ? null : r.text().trim());
            ans.setRatingValue(r.rating());
            answers.save(ans);
        }
        answers.flush();
    }

    /** Soumission : toutes les questions ont une réponse du bon type. */
    private void requireComplete(Appraisal a, Author author) {
        Map<UUID, AppraisalAnswer> given = answers.findByAppraisalIdAndAuthor(a.getId(), author).stream()
                .collect(Collectors.toMap(AppraisalAnswer::getQuestionId, Function.identity()));
        var errors = new ValidationException.Collector();
        for (AppraisalQuestion q : questions.findByCampaignIdOrderByPositionAsc(a.getCampaignId())) {
            AppraisalAnswer ans = given.get(q.getId());
            boolean ok = ans != null && (q.getType() == QuestionType.TEXT ? ans.getTextValue() != null : ans.getRatingValue() != null);
            errors.check(ok, "answers[" + q.getId() + "]", "réponse manquante : « " + q.getQuestion() + " »");
        }
        errors.throwIfAny();
    }

    private Appraisal visible(AuthUser me, UUID id) {
        Appraisal a = appraisals.findById(id).orElseThrow(() -> ApiException.notFound("Entretien"));
        visibleOrThrow(me, a);
        return a;
    }

    private void visibleOrThrow(AuthUser me, Appraisal a) {
        if (!me.isAdmin() && !a.getEmployeeId().equals(me.id()) && !a.getManagerId().equals(me.id())) {
            throw ApiException.notFound("Entretien");
        }
    }

    // ---------- conversions ----------

    private List<AnswerDto> answersOf(Appraisal a, Author author) {
        return answers.findByAppraisalIdAndAuthor(a.getId(), author).stream()
                .map(x -> new AnswerDto(x.getQuestionId(), x.getTextValue(), x.getRatingValue())).toList();
    }

    private CampaignDto toDto(AppraisalCampaign c) {
        return new CampaignDto(c.getId(), c.getYear(), c.getDeadline(), questionsOf(c.getId()), appraisals.findByCampaignId(c.getId()).size());
    }

    private List<QuestionDto> questionsOf(UUID campaignId) {
        return questions.findByCampaignIdOrderByPositionAsc(campaignId).stream()
                .map(q -> new QuestionDto(q.getId(), q.getPosition(), q.getQuestion(), q.getType())).toList();
    }

    private AppraisalDto detail(AuthUser me, Appraisal a) {
        AppraisalCampaign c = campaigns.findById(a.getCampaignId()).orElseThrow();
        boolean employee = a.getEmployeeId().equals(me.id());
        boolean manager = a.getManagerId().equals(me.id());
        String role = employee ? "EMPLOYEE" : manager ? "MANAGER" : "ADMIN";
        boolean seeSelf = employee || a.getSelfSubmittedAt() != null;
        boolean seeManager = manager || me.isAdmin() || a.getManagerSubmittedAt() != null;
        boolean editable = open(a) && switch (a.getStep()) {
            case SELF_REVIEW, EMPLOYEE_SIGNATURE -> employee;
            case MANAGER_REVIEW, MANAGER_SIGNATURE -> manager;
            case COMPLETED -> false;
        };
        List<ObjectiveDto> objs = seeManager ? objectives.findByAppraisalIdOrderByPositionAsc(a.getId()).stream()
                .map(o -> new ObjectiveDto(o.getPosition(), o.getTitle(), o.getDescription())).toList() : List.of();
        return new AppraisalDto(a.getId(), c.getId(), c.getYear(), c.getDeadline(), a.getEmployeeId(), userLookup.name(a.getEmployeeId()),
                a.getManagerId(), userLookup.name(a.getManagerId()), a.getStep(), role, editable, deadlinePassed(c), a.getReopenedUntil(),
                questionsOf(c.getId()), seeSelf ? answersOf(a, Author.SELF) : null, seeManager ? answersOf(a, Author.MANAGER) : null,
                objs, a.getFinalComment(), a.getEmployeeSignedAt(), a.getManagerSignedAt());
    }
}
