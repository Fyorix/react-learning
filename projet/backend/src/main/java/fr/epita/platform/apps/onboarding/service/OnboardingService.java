package fr.epita.platform.apps.onboarding.service;

import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.JourneyDto;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.JourneyRequest;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.TaskDto;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.TemplateDto;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.TemplateRequest;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.TemplateTaskDto;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.TemplateTaskRequest;
import fr.epita.platform.apps.onboarding.entity.Journey;
import fr.epita.platform.apps.onboarding.entity.JourneyTask;
import fr.epita.platform.apps.onboarding.entity.Template;
import fr.epita.platform.apps.onboarding.entity.TemplateTask;
import fr.epita.platform.apps.onboarding.repository.JourneyRepository;
import fr.epita.platform.apps.onboarding.repository.TemplateRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final TemplateRepository templates;
    private final JourneyRepository journeys;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- modèles ----------

    @Transactional(readOnly = true)
    public List<TemplateDto> templates(AuthUser me) {
        me.requireAdmin();
        return templates.findAllByOrderByNameAsc().stream().map(OnboardingService::toDto).toList();
    }

    @Transactional
    public TemplateDto createTemplate(AuthUser me, TemplateRequest req) {
        me.requireAdmin();
        if (templates.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("TEMPLATE_NAME_EXISTS", "Un modèle porte déjà ce nom");
        }
        Template t = new Template();
        apply(t, req);
        return toDto(templates.saveAndFlush(t));
    }

    @Transactional
    public TemplateDto updateTemplate(AuthUser me, UUID id, TemplateRequest req) {
        me.requireAdmin();
        Template t = template(id);
        if (templates.existsByNameIgnoreCaseAndIdNot(req.name().trim(), id)) {
            throw ApiException.conflict("TEMPLATE_NAME_EXISTS", "Un modèle porte déjà ce nom");
        }
        apply(t, req);
        return toDto(templates.saveAndFlush(t));
    }

    @Transactional
    public void deleteTemplate(AuthUser me, UUID id) {
        me.requireAdmin();
        templates.delete(template(id));
    }

    // ---------- parcours ----------

    /** Les tâches sont copiées avec leurs échéances calculées à partir de la date d'arrivée. */
    @Transactional
    public JourneyDto launch(AuthUser me, JourneyRequest req) {
        me.requireAdmin();
        Template t = template(req.templateId());
        var errors = new ValidationException.Collector();
        errors.check(!req.managerId().equals(req.newcomerId()), "managerId", "doit être différent de l'arrivant");
        errors.check(!req.buddyId().equals(req.newcomerId()), "buddyId", "doit être différent de l'arrivant");
        errors.check(!req.buddyId().equals(req.managerId()), "buddyId", "doit être différent du manager");
        errors.throwIfAny();
        userLookup.require(req.newcomerId());
        userLookup.require(req.managerId());
        userLookup.require(req.buddyId());
        boolean active = journeys.findByNewcomerIdOrderByStartDateDesc(req.newcomerId()).stream().anyMatch(j -> !j.isCompleted());
        if (active) {
            throw ApiException.conflict("ONBOARDING_ALREADY_ACTIVE", "Cet arrivant a déjà un parcours en cours");
        }
        Journey j = new Journey();
        j.setTemplateName(t.getName());
        j.setNewcomerId(req.newcomerId());
        j.setManagerId(req.managerId());
        j.setBuddyId(req.buddyId());
        j.setStartDate(req.startDate());
        for (TemplateTask tt : t.getTasks()) {
            JourneyTask task = new JourneyTask();
            task.setJourney(j);
            task.setTitle(tt.getTitle());
            task.setDescription(tt.getDescription());
            task.setOwner(tt.getOwner());
            task.setDueDate(req.startDate().plusDays(tt.getDueOffsetDays()));
            task.setMandatory(tt.isMandatory());
            task.setPosition(tt.getPosition());
            j.getTasks().add(task);
        }
        return toDto(journeys.saveAndFlush(j), me);
    }

    @Transactional(readOnly = true)
    public List<JourneyDto> list(AuthUser me, String status, Boolean overdue) {
        return journeys.findAllByOrderByStartDateDesc().stream()
                .filter(j -> me.isAdmin() || j.getManagerId().equals(me.id()) || j.getBuddyId().equals(me.id()))
                .map(j -> toDto(j, me))
                .filter(d -> status == null || d.status().equals(status))
                .filter(d -> overdue == null || (d.overdue() > 0) == overdue)
                .toList();
    }

    @Transactional(readOnly = true)
    public JourneyDto mine(AuthUser me) {
        return journeys.findByNewcomerIdOrderByStartDateDesc(me.id()).stream().findFirst()
                .map(j -> toDto(j, me)).orElseThrow(() -> ApiException.notFound("Parcours"));
    }

    @Transactional(readOnly = true)
    public JourneyDto get(AuthUser me, UUID id) {
        Journey j = journey(id);
        if (!me.isAdmin() && !List.of(j.getNewcomerId(), j.getManagerId(), j.getBuddyId()).contains(me.id())) {
            throw ApiException.notFound("Parcours");
        }
        return toDto(j, me);
    }

    @Transactional
    public JourneyDto toggle(AuthUser me, UUID journeyId, UUID taskId, boolean done) {
        Journey j = journey(journeyId);
        JourneyTask task = j.getTasks().stream().filter(t -> t.getId().equals(taskId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Tâche"));
        if (!canToggle(me, j, task)) {
            throw ApiException.forbidden("Seul le responsable de la tâche peut la cocher");
        }
        task.setDoneAt(done ? clock.instant() : null);
        task.setDoneBy(done ? me.id() : null);
        return toDto(j, me);
    }

    // ---------- outils ----------

    static boolean canToggle(AuthUser me, Journey j, JourneyTask t) {
        if (me.isAdmin()) {
            return true;
        }
        return switch (t.getOwner()) {
            case NEWCOMER -> me.id().equals(j.getNewcomerId());
            case MANAGER -> me.id().equals(j.getManagerId());
            case BUDDY -> me.id().equals(j.getBuddyId());
            case IT, HR -> false;
        };
    }

    private static void apply(Template t, TemplateRequest req) {
        t.setName(req.name().trim());
        t.getTasks().clear();
        int pos = 0;
        for (TemplateTaskRequest r : req.tasks()) {
            TemplateTask task = new TemplateTask();
            task.setTemplate(t);
            task.setTitle(r.title().trim());
            task.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
            task.setOwner(r.owner());
            task.setDueOffsetDays(r.dueOffsetDays());
            task.setMandatory(r.mandatory());
            task.setPosition(pos++);
            t.getTasks().add(task);
        }
    }

    private Template template(UUID id) {
        return templates.findById(id).orElseThrow(() -> ApiException.notFound("Modèle"));
    }

    private Journey journey(UUID id) {
        return journeys.findById(id).orElseThrow(() -> ApiException.notFound("Parcours"));
    }

    private static TemplateDto toDto(Template t) {
        return new TemplateDto(t.getId(), t.getName(), t.getTasks().stream().map(x -> new TemplateTaskDto(x.getId(), x.getTitle(),
                x.getDescription(), x.getOwner(), x.getDueOffsetDays(), x.isMandatory())).toList());
    }

    private JourneyDto toDto(Journey j, AuthUser me) {
        LocalDate today = LocalDate.now(clock);
        List<TaskDto> tasks = j.getTasks().stream().map(t -> new TaskDto(t.getId(), t.getTitle(), t.getDescription(),
                t.getOwner(), t.getDueDate(), t.isMandatory(), t.isDone(), t.getDoneAt(), userLookup.name(t.getDoneBy()),
                !t.isDone() && t.getDueDate().isBefore(today), canToggle(me, j, t))).toList();
        int done = (int) tasks.stream().filter(TaskDto::done).count();
        int overdue = (int) tasks.stream().filter(TaskDto::overdue).count();
        int progress = tasks.isEmpty() ? 100 : Math.round(done * 100f / tasks.size());
        return new JourneyDto(j.getId(), j.getTemplateName(), j.getNewcomerId(), userLookup.name(j.getNewcomerId()),
                j.getManagerId(), userLookup.name(j.getManagerId()), j.getBuddyId(), userLookup.name(j.getBuddyId()),
                j.getStartDate(), j.isCompleted() ? "COMPLETED" : "IN_PROGRESS", done, tasks.size(), progress, overdue, tasks);
    }
}
