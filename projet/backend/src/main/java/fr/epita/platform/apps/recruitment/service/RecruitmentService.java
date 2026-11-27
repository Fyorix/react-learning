package fr.epita.platform.apps.recruitment.service;

import fr.epita.platform.apps.recruitment.dto.RecruitmentDtos.*;
import fr.epita.platform.apps.recruitment.entity.*;
import fr.epita.platform.apps.recruitment.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.FileStorage;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** Pipeline : on avance (motif si on saute une étape), on ne recule jamais ; REJECTED depuis toute étape non finale. */
@Service
@RequiredArgsConstructor
public class RecruitmentService {

    public static final String ROLES = "recruitment_roles";
    public static final String RECRUITER = "RECRUITER";
    static final int APPROVALS_FOR_OFFER = 2;

    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final StageEventRepository events;
    private final EvaluationRepository evaluations;
    private final AppRoles roles;
    private final FileStorage files;
    private final UserLookup userLookup;
    private final Clock clock;

    public record Cv(byte[] content, String fileName) {
    }

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(RECRUITER));
    }

    // ---------- postes ----------

    @Transactional(readOnly = true)
    public List<JobDto> jobs(JobStatus status) {
        return jobs.findAllByOrderByCreatedAtDesc().stream().filter(j -> status == null || j.getStatus() == status).map(this::toDto).toList();
    }

    @Transactional
    public JobDto createJob(AuthUser me, JobRequest req) {
        roles.require(ROLES, me, RECRUITER);
        Job j = new Job();
        j.setCreatedBy(me.id());
        apply(j, req);
        return toDto(jobs.saveAndFlush(j));
    }

    @Transactional
    public JobDto updateJob(AuthUser me, UUID id, JobRequest req) {
        roles.require(ROLES, me, RECRUITER);
        Job j = job(id);
        apply(j, req);
        return toDto(jobs.saveAndFlush(j));
    }

    private void apply(Job j, JobRequest req) {
        j.setTitle(req.title().trim());
        j.setTeam(req.team().trim());
        j.setDescription(req.description().trim());
        j.setLocation(req.location().trim());
        j.setStatus(req.status() == null ? JobStatus.OPEN : req.status());
    }

    // ---------- candidatures ----------

    @Transactional
    public ApplicationDto apply(AuthUser me, UUID jobId, CandidateRequest req, MultipartFile cv) {
        roles.require(ROLES, me, RECRUITER);
        Job j = job(jobId);
        if (j.getStatus() != JobStatus.OPEN) {
            throw ApiException.conflict("JOB_NOT_OPEN", "Le poste n'accepte plus de candidature (" + j.getStatus() + ")");
        }
        String email = req.email().trim().toLowerCase();
        if (applications.existsByJobIdAndEmailIgnoreCase(jobId, email)) {
            throw ApiException.conflict("ALREADY_APPLIED", "Ce candidat a déjà postulé à ce poste");
        }
        FileStorage.Stored stored = files.store(cv, FileStorage.PDF);
        Application a = new Application();
        a.setJobId(jobId);
        a.setFirstName(req.firstName().trim());
        a.setLastName(req.lastName().trim());
        a.setEmail(email);
        a.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        a.setCvKey(stored.key());
        a.setCvName(stored.originalName());
        a.setStage(Stage.SCREENING);
        a.setStageSince(clock.instant());
        applications.saveAndFlush(a);
        event(a, null, Stage.SCREENING, null, me.id());
        return detail(me, a);
    }

    @Transactional(readOnly = true)
    public PipelineDto pipeline(AuthUser me, UUID jobId) {
        roles.require(ROLES, me, RECRUITER);
        Job j = job(jobId);
        Map<Stage, List<ApplicationSummary>> byStage = new EnumMap<>(Stage.class);
        for (Stage s : Stage.values()) {
            byStage.put(s, new ArrayList<>());
        }
        applications.findByJobIdOrderByStageSinceAsc(jobId).forEach(a -> {
            List<Evaluation> evs = evaluations.findByApplicationIdOrderByCreatedAtAsc(a.getId());
            byStage.get(a.getStage()).add(new ApplicationSummary(a.getId(), a.getFirstName() + " " + a.getLastName(), a.getEmail(),
                    a.getStage(), a.getStageSince(), evs.size(), (int) evs.stream().filter(e -> e.getRecommendation() == Recommendation.YES).count()));
        });
        return new PipelineDto(toDto(j), byStage.entrySet().stream().map(e -> new StageColumn(e.getKey(), e.getValue())).toList());
    }

    @Transactional(readOnly = true)
    public ApplicationDto get(AuthUser me, UUID id) {
        return detail(me, visible(me, id));
    }

    @Transactional(readOnly = true)
    public Cv cv(AuthUser me, UUID id) {
        Application a = visible(me, id);
        return new Cv(files.load(a.getCvKey()), a.getCvName());
    }

    @Transactional
    public ApplicationDto move(AuthUser me, UUID id, MoveRequest req) {
        roles.require(ROLES, me, RECRUITER);
        Application a = applications.lock(id).orElseThrow(() -> ApiException.notFound("Candidature"));
        Stage from = a.getStage();
        Stage to = req.stage();
        String reason = req.reason() == null || req.reason().isBlank() ? null : req.reason().trim();
        if (req.interviewerIds() != null) {
            req.interviewerIds().forEach(userLookup::require);
            a.setInterviewerIds(new HashSet<>(req.interviewerIds()));
        }
        if (to == from && req.interviewerIds() != null) {
            return detail(me, applications.saveAndFlush(a));
        }
        if (from.isFinal() || to == from || (to != Stage.REJECTED && Stage.PIPELINE.indexOf(to) < Stage.PIPELINE.indexOf(from))) {
            throw ApiException.conflict("INVALID_STAGE_TRANSITION", "Passage de " + from + " à " + to + " interdit");
        }
        if (to == Stage.REJECTED && reason == null) {
            throw ValidationException.field("reason", "obligatoire pour un refus");
        }
        if (to != Stage.REJECTED && Stage.PIPELINE.indexOf(to) > Stage.PIPELINE.indexOf(from) + 1 && reason == null) {
            throw ValidationException.field("reason", "obligatoire pour sauter une étape");
        }
        if (Stage.PIPELINE.indexOf(to) >= Stage.PIPELINE.indexOf(Stage.OFFER)
                && evaluations.countByApplicationIdAndRecommendation(id, Recommendation.YES) < APPROVALS_FOR_OFFER) {
            throw ApiException.conflict("NOT_ENOUGH_APPROVALS", "Une offre nécessite au moins " + APPROVALS_FOR_OFFER + " évaluations favorables");
        }
        a.setStage(to);
        a.setStageSince(clock.instant());
        if (to == Stage.HIRED) {
            a.setHiredAt(clock.instant());
            Job j = job(a.getJobId());
            j.setStatus(JobStatus.FILLED);
            jobs.save(j);
        }
        applications.saveAndFlush(a);
        event(a, from, to, reason, me.id());
        return detail(me, a);
    }

    // ---------- évaluations ----------

    @Transactional
    public ApplicationDto evaluate(AuthUser me, UUID id, EvaluationRequest req) {
        Application a = visible(me, id);
        if (!canEvaluate(me, a)) {
            throw ApiException.forbidden("Vous n'êtes pas intervenant sur cette candidature");
        }
        if (req.stage() != a.getStage() || a.getStage().isFinal()) {
            throw ApiException.conflict("INVALID_STAGE_TRANSITION", "Seule l'étape en cours (" + a.getStage() + ") peut être évaluée");
        }
        if (evaluations.existsByApplicationIdAndEvaluatorIdAndStage(id, me.id(), req.stage())) {
            throw ApiException.conflict("ALREADY_EVALUATED", "Vous avez déjà évalué cette étape");
        }
        Evaluation e = new Evaluation();
        e.setApplicationId(id);
        e.setEvaluatorId(me.id());
        e.setStage(req.stage());
        e.setRating(req.rating());
        e.setRecommendation(req.recommendation());
        e.setComment(req.comment().trim());
        evaluations.saveAndFlush(e);
        return detail(me, a);
    }

    /** Candidatures où je suis intervenant, étape en cours non finale et pas encore évaluée par moi. */
    @Transactional(readOnly = true)
    public List<TodoDto> myTodo(AuthUser me) {
        return applications.assignedTo(me.id()).stream()
                .filter(a -> !a.getStage().isFinal() && !evaluations.existsByApplicationIdAndEvaluatorIdAndStage(a.getId(), me.id(), a.getStage()))
                .sorted(Comparator.comparing(Application::getStageSince))
                .map(a -> new TodoDto(a.getId(), a.getFirstName() + " " + a.getLastName(), job(a.getJobId()).getTitle(), a.getStage(), a.getStageSince()))
                .toList();
    }

    // ---------- statistiques ----------

    @Transactional(readOnly = true)
    public StatsDto stats(AuthUser me) {
        roles.require(ROLES, me, RECRUITER);
        List<Application> all = applications.findAll();
        Map<Stage, Long> reached = new EnumMap<>(Stage.class);
        for (Stage s : Stage.PIPELINE) {
            reached.put(s, 0L);
        }
        for (Application a : all) {
            int max = events.findByApplicationIdOrderByCreatedAtAsc(a.getId()).stream()
                    .map(StageEvent::getToStage).filter(s -> s != Stage.REJECTED).mapToInt(Stage.PIPELINE::indexOf).max().orElse(0);
            for (int i = 0; i <= max; i++) {
                reached.merge(Stage.PIPELINE.get(i), 1L, Long::sum);
            }
        }
        Map<Stage, Integer> pass = new EnumMap<>(Stage.class);
        for (int i = 0; i < Stage.PIPELINE.size() - 1; i++) {
            long here = reached.get(Stage.PIPELINE.get(i));
            long next = reached.get(Stage.PIPELINE.get(i + 1));
            pass.put(Stage.PIPELINE.get(i), here == 0 ? 0 : (int) Math.round(next * 100.0 / here));
        }
        List<Application> hired = all.stream().filter(a -> a.getHiredAt() != null).toList();
        Double avg = hired.isEmpty() ? null : hired.stream()
                .mapToDouble(a -> Duration.between(a.getCreatedAt(), a.getHiredAt()).toHours() / 24.0).average().orElse(0);
        return new StatsDto(avg == null ? null : Math.round(avg * 10) / 10.0, hired.size(), pass, reached);
    }

    // ---------- garde-fous et conversions ----------

    private boolean canEvaluate(AuthUser me, Application a) {
        return a.getInterviewerIds().contains(me.id()) || roles.has(ROLES, me, RECRUITER);
    }

    private Application visible(AuthUser me, UUID id) {
        Application a = applications.findById(id).orElseThrow(() -> ApiException.notFound("Candidature"));
        if (!roles.has(ROLES, me, RECRUITER) && !a.getInterviewerIds().contains(me.id())) {
            throw ApiException.notFound("Candidature");
        }
        return a;
    }

    private Job job(UUID id) {
        return jobs.findById(id).orElseThrow(() -> ApiException.notFound("Poste"));
    }

    private void event(Application a, Stage from, Stage to, String reason, UUID by) {
        StageEvent e = new StageEvent();
        e.setApplicationId(a.getId());
        e.setFromStage(from);
        e.setToStage(to);
        e.setReason(reason);
        e.setMovedBy(by);
        events.saveAndFlush(e);
    }

    private JobDto toDto(Job j) {
        return new JobDto(j.getId(), j.getTitle(), j.getTeam(), j.getDescription(), j.getLocation(), j.getStatus(),
                applications.countByJobId(j.getId()), j.getCreatedAt());
    }

    private ApplicationDto detail(AuthUser me, Application a) {
        List<StageEventDto> history = events.findByApplicationIdOrderByCreatedAtAsc(a.getId()).stream()
                .map(e -> new StageEventDto(e.getFromStage(), e.getToStage(), e.getReason(), e.getMovedBy() == null ? null : userLookup.name(e.getMovedBy()), e.getCreatedAt()))
                .toList();
        List<Evaluation> evs = evaluations.findByApplicationIdOrderByCreatedAtAsc(a.getId());
        Map<UUID, String> names = userLookup.namesOf(evs, Evaluation::getEvaluatorId);
        List<EvaluationDto> evDtos = evs.stream().map(e -> new EvaluationDto(e.getId(), e.getEvaluatorId(), names.get(e.getEvaluatorId()),
                e.getStage(), e.getRating(), e.getRecommendation(), e.getComment(), e.getCreatedAt())).toList();
        Map<UUID, String> interviewerNames = userLookup.names(a.getInterviewerIds());
        List<InterviewerDto> interviewers = a.getInterviewerIds().stream().map(u -> new InterviewerDto(u, interviewerNames.get(u)))
                .sorted(Comparator.comparing(InterviewerDto::name, Comparator.nullsLast(String::compareTo))).toList();
        boolean canEvaluate = !a.getStage().isFinal() && canEvaluate(me, a)
                && !evaluations.existsByApplicationIdAndEvaluatorIdAndStage(a.getId(), me.id(), a.getStage());
        return new ApplicationDto(a.getId(), a.getJobId(), job(a.getJobId()).getTitle(), a.getFirstName(), a.getLastName(), a.getEmail(),
                a.getPhone(), a.getCvName(), a.getStage(), a.getStageSince(), a.getCreatedAt(), interviewers, history, evDtos, canEvaluate);
    }
}
