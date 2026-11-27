package fr.epita.platform.apps.volunteering.service;

import fr.epita.platform.apps.volunteering.dto.VolunteeringDtos.*;
import fr.epita.platform.apps.volunteering.entity.Association;
import fr.epita.platform.apps.volunteering.entity.Mission;
import fr.epita.platform.apps.volunteering.entity.VolunteerRegistration;
import fr.epita.platform.apps.volunteering.entity.VolunteerStatus;
import fr.epita.platform.apps.volunteering.repository.AssociationRepository;
import fr.epita.platform.apps.volunteering.repository.MissionRepository;
import fr.epita.platform.apps.volunteering.repository.VolunteerRegistrationRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plafond annuel : 14 h par salarié, calculé sur les inscriptions en attente et approuvées de l'année
 * (heures déclarées si saisies, durée prévue sinon) ; revérifié à l'approbation.
 */
@Service
@RequiredArgsConstructor
public class VolunteeringService {

    public static final String REPORTS = "volunteering_reports";
    static final int YEARLY_LIMIT = 14;
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Set<VolunteerStatus> HOLDING = EnumSet.of(VolunteerStatus.REQUESTED, VolunteerStatus.APPROVED);

    private final AssociationRepository associations;
    private final MissionRepository missions;
    private final VolunteerRegistrationRepository registrations;
    private final ManagerRelations relations;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- associations et missions ----------

    @Transactional(readOnly = true)
    public List<AssociationDto> associations(AuthUser me) {
        return associations.findAllByOrderByNameAsc().stream().filter(a -> me.isAdmin() || a.isActive())
                .map(a -> new AssociationDto(a.getId(), a.getName(), a.getCause(), a.isActive())).toList();
    }

    @Transactional
    public AssociationDto addAssociation(AuthUser me, AssociationRequest req) {
        me.requireAdmin();
        if (associations.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("ASSOCIATION_EXISTS", "Cette association existe déjà");
        }
        Association a = new Association();
        a.setName(req.name().trim());
        a.setCause(req.cause().trim());
        a.setActive(req.active());
        associations.saveAndFlush(a);
        return new AssociationDto(a.getId(), a.getName(), a.getCause(), a.isActive());
    }

    @Transactional
    public AssociationDto updateAssociation(AuthUser me, UUID id, AssociationRequest req) {
        me.requireAdmin();
        Association a = associations.findById(id).orElseThrow(() -> ApiException.notFound("Association"));
        a.setName(req.name().trim());
        a.setCause(req.cause().trim());
        a.setActive(req.active());
        associations.saveAndFlush(a);
        return new AssociationDto(a.getId(), a.getName(), a.getCause(), a.isActive());
    }

    @Transactional
    public MissionDto createMission(AuthUser me, MissionRequest req) {
        me.requireAdmin();
        Association a = associations.findById(req.associationId()).orElseThrow(() -> ApiException.notFound("Association"));
        if (!a.isActive()) {
            throw ApiException.conflict("ASSOCIATION_INACTIVE", "Cette association est désactivée");
        }
        if (!req.date().isAfter(today())) {
            throw ValidationException.field("date", "doit être dans le futur");
        }
        Mission m = new Mission();
        m.setAssociationId(a.getId());
        m.setTitle(req.title().trim());
        m.setMissionDate(req.date());
        m.setDurationHours(req.durationHours());
        m.setLocation(req.location().trim());
        m.setSkills(req.skills() == null ? "" : req.skills().stream().map(String::trim).filter(s -> !s.isEmpty() && !s.contains(","))
                .distinct().collect(Collectors.joining(",")));
        m.setSeats(req.seats());
        return toDto(me, missions.saveAndFlush(m));
    }

    /** Missions à venir des associations actives. */
    @Transactional(readOnly = true)
    public List<MissionDto> missions(AuthUser me, String cause, LocalDate from, LocalDate to) {
        LocalDate start = from == null || from.isBefore(today()) ? today() : from;
        LocalDate end = to == null ? start.plusYears(1) : to;
        Map<UUID, Association> byId = associations.findAll().stream().collect(Collectors.toMap(Association::getId, Function.identity()));
        return missions.findByMissionDateBetweenOrderByMissionDateAsc(start, end).stream()
                .filter(m -> byId.get(m.getAssociationId()).isActive())
                .filter(m -> cause == null || cause.isBlank() || byId.get(m.getAssociationId()).getCause().equalsIgnoreCase(cause))
                .map(m -> toDto(me, m)).toList();
    }

    @Transactional(readOnly = true)
    public MissionDto mission(AuthUser me, UUID id) {
        return toDto(me, missions.findById(id).orElseThrow(() -> ApiException.notFound("Mission")));
    }

    // ---------- inscriptions ----------

    @Transactional
    public RegistrationDto register(AuthUser me, UUID missionId) {
        Mission m = missions.lock(missionId).orElseThrow(() -> ApiException.notFound("Mission"));
        Association a = associations.findById(m.getAssociationId()).orElseThrow();
        if (!a.isActive() || !m.getMissionDate().isAfter(today())) {
            throw ApiException.conflict("MISSION_CLOSED", "Cette mission n'est plus ouverte aux inscriptions");
        }
        Optional<VolunteerRegistration> existing = registrations.findByMissionIdAndUserId(missionId, me.id());
        if (existing.isPresent() && existing.get().getStatus() != VolunteerStatus.CANCELLED) {
            throw ApiException.conflict("ALREADY_REGISTERED", "Vous êtes déjà inscrit à cette mission");
        }
        if (registrations.countByMissionIdAndStatusIn(missionId, HOLDING) >= m.getSeats()) {
            throw ApiException.conflict("MISSION_FULL", "Cette mission est complète");
        }
        checkYearlyLimit(me.id(), m, null);
        VolunteerRegistration r = existing.orElseGet(VolunteerRegistration::new);
        r.setMissionId(missionId);
        r.setUserId(me.id());
        r.setStatus(VolunteerStatus.REQUESTED);
        r.setDecidedBy(null);
        r.setReason(null);
        return toDto(registrations.saveAndFlush(r), m);
    }

    @Transactional
    public void unregister(AuthUser me, UUID missionId) {
        Mission m = missions.lock(missionId).orElseThrow(() -> ApiException.notFound("Mission"));
        VolunteerRegistration r = registrations.findByMissionIdAndUserId(missionId, me.id()).filter(x -> HOLDING.contains(x.getStatus()))
                .orElseThrow(() -> ApiException.notFound("Inscription"));
        if (!m.getMissionDate().isAfter(today())) {
            throw ApiException.conflict("MISSION_CLOSED", "La mission a eu lieu");
        }
        r.setStatus(VolunteerStatus.CANCELLED);
        registrations.saveAndFlush(r);
    }

    @Transactional(readOnly = true)
    public List<RegistrationDto> team(AuthUser me, VolunteerStatus status) {
        List<UUID> team = relations.employeesOf(REPORTS, me.id());
        List<VolunteerRegistration> list = me.isAdmin() && team.isEmpty() ? registrations.findByStatus(status == null ? VolunteerStatus.REQUESTED : status)
                : team.isEmpty() ? List.of() : registrations.findByUserIdInAndStatus(team, status == null ? VolunteerStatus.REQUESTED : status);
        return list.stream().map(r -> toDto(r, missions.findById(r.getMissionId()).orElseThrow()))
                .sorted(Comparator.comparing(RegistrationDto::date)).toList();
    }

    @Transactional
    public RegistrationDto approve(AuthUser me, UUID id) {
        VolunteerRegistration r = decidable(me, id);
        Mission m = missions.lock(r.getMissionId()).orElseThrow();
        checkYearlyLimit(r.getUserId(), m, r.getId());
        r.setStatus(VolunteerStatus.APPROVED);
        r.setDecidedBy(me.id());
        return toDto(registrations.saveAndFlush(r), m);
    }

    @Transactional
    public RegistrationDto reject(AuthUser me, UUID id, String reason) {
        VolunteerRegistration r = decidable(me, id);
        r.setStatus(VolunteerStatus.REJECTED);
        r.setDecidedBy(me.id());
        r.setReason(reason.trim());
        return toDto(registrations.saveAndFlush(r), missions.findById(r.getMissionId()).orElseThrow());
    }

    @Transactional
    public RegistrationDto declareHours(AuthUser me, UUID id, HoursRequest req) {
        VolunteerRegistration r = registrations.findById(id).filter(x -> x.getUserId().equals(me.id()))
                .orElseThrow(() -> ApiException.notFound("Inscription"));
        Mission m = missions.findById(r.getMissionId()).orElseThrow();
        if (r.getStatus() != VolunteerStatus.APPROVED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seule une inscription approuvée donne lieu à une déclaration");
        }
        if (!today().isAfter(m.getMissionDate())) {
            throw ApiException.conflict("MISSION_NOT_DONE", "Les heures se déclarent après la date de la mission");
        }
        if (req.hours().compareTo(BigDecimal.valueOf(m.getDurationHours())) > 0) {
            throw ValidationException.field("hours", "plafonnées à la durée de la mission (" + m.getDurationHours() + " h)");
        }
        if (req.hours().remainder(new BigDecimal("0.5")).signum() != 0) {
            throw ValidationException.field("hours", "par demi-heure");
        }
        r.setHours(req.hours());
        r.setComment(req.comment() == null || req.comment().isBlank() ? null : req.comment().trim());
        return toDto(registrations.saveAndFlush(r), m);
    }

    // ---------- compteurs ----------

    @Transactional(readOnly = true)
    public MeDto me(AuthUser me) {
        int year = today().getYear();
        List<VolunteerRegistration> mine = registrations.findByUserIdOrderByCreatedAtDesc(me.id());
        Map<UUID, Mission> byId = missions.findByIdIn(mine.stream().map(VolunteerRegistration::getMissionId).toList()).stream()
                .collect(Collectors.toMap(Mission::getId, Function.identity()));
        BigDecimal planned = BigDecimal.ZERO;
        BigDecimal declared = BigDecimal.ZERO;
        for (VolunteerRegistration r : mine) {
            Mission m = byId.get(r.getMissionId());
            if (m.getMissionDate().getYear() == year && HOLDING.contains(r.getStatus())) {
                planned = planned.add(weight(r, m));
                if (r.getHours() != null) {
                    declared = declared.add(r.getHours());
                }
            }
        }
        List<RegistrationDto> list = mine.stream().map(r -> toDto(r, byId.get(r.getMissionId())))
                .sorted(Comparator.comparing(RegistrationDto::date).reversed()).toList();
        return new MeDto(year, planned, declared, YEARLY_LIMIT, BigDecimal.valueOf(YEARLY_LIMIT).subtract(planned).max(BigDecimal.ZERO), list);
    }

    @Transactional(readOnly = true)
    public ReportDto report(AuthUser me, Integer year) {
        me.requireAdmin();
        int y = year == null ? today().getYear() : year;
        Map<UUID, Mission> ms = missions.findAll().stream().filter(m -> m.getMissionDate().getYear() == y)
                .collect(Collectors.toMap(Mission::getId, Function.identity()));
        Map<UUID, String> assoc = associations.findAll().stream().collect(Collectors.toMap(Association::getId, Association::getName));
        List<VolunteerRegistration> done = registrations.findByStatus(VolunteerStatus.APPROVED).stream()
                .filter(r -> ms.containsKey(r.getMissionId()) && r.getHours() != null).toList();
        Map<UUID, String> names = userLookup.namesOf(done, VolunteerRegistration::getUserId);
        BigDecimal total = done.stream().map(VolunteerRegistration::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReportDto(y, total, done.stream().map(VolunteerRegistration::getUserId).distinct().count(),
                rows(done, r -> names.get(r.getUserId())), rows(done, r -> assoc.get(ms.get(r.getMissionId()).getAssociationId())));
    }

    private static List<ReportRow> rows(List<VolunteerRegistration> done, Function<VolunteerRegistration, String> key) {
        return done.stream().collect(Collectors.groupingBy(key)).entrySet().stream()
                .map(e -> new ReportRow(e.getKey(), e.getValue().stream().map(VolunteerRegistration::getHours).reduce(BigDecimal.ZERO, BigDecimal::add), e.getValue().size()))
                .sorted(Comparator.comparing(ReportRow::hours).reversed()).toList();
    }

    // ---------- règles ----------

    private void checkYearlyLimit(UUID userId, Mission target, UUID excludeRegistration) {
        int year = target.getMissionDate().getYear();
        List<VolunteerRegistration> mine = registrations.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(r -> HOLDING.contains(r.getStatus()) && !r.getId().equals(excludeRegistration) && !r.getMissionId().equals(target.getId())).toList();
        Map<UUID, Mission> byId = missions.findByIdIn(mine.stream().map(VolunteerRegistration::getMissionId).toList()).stream()
                .collect(Collectors.toMap(Mission::getId, Function.identity()));
        BigDecimal used = mine.stream().filter(r -> byId.get(r.getMissionId()).getMissionDate().getYear() == year)
                .map(r -> weight(r, byId.get(r.getMissionId()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (used.add(BigDecimal.valueOf(target.getDurationHours())).compareTo(BigDecimal.valueOf(YEARLY_LIMIT)) > 0) {
            throw ApiException.conflict("YEARLY_LIMIT_REACHED", "Plafond annuel de " + YEARLY_LIMIT + " h atteint (" + used.stripTrailingZeros().toPlainString() + " h déjà engagées)");
        }
    }

    /** Heures déclarées si saisies, sinon durée prévue de la mission. */
    private BigDecimal weight(VolunteerRegistration r, Mission m) {
        return r.getHours() != null ? r.getHours() : BigDecimal.valueOf(m.getDurationHours());
    }

    private VolunteerRegistration decidable(AuthUser me, UUID id) {
        VolunteerRegistration r = registrations.findById(id).orElseThrow(() -> ApiException.notFound("Inscription"));
        if (!me.isAdmin() && !relations.isManagerOf(REPORTS, me.id(), r.getUserId())) {
            throw ApiException.forbidden("Réservé au manager du salarié");
        }
        if (r.getStatus() != VolunteerStatus.REQUESTED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Inscription déjà " + r.getStatus());
        }
        return r;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private MissionDto toDto(AuthUser me, Mission m) {
        Association a = associations.findById(m.getAssociationId()).orElseThrow();
        long taken = registrations.countByMissionIdAndStatusIn(m.getId(), HOLDING);
        VolunteerStatus mine = registrations.findByMissionIdAndUserId(m.getId(), me.id()).map(VolunteerRegistration::getStatus).orElse(null);
        return new MissionDto(m.getId(), a.getId(), a.getName(), a.getCause(), m.getTitle(), m.getMissionDate(), m.getDurationHours(),
                m.getLocation(), m.skillList(), m.getSeats(), (int) Math.max(0, m.getSeats() - taken), mine);
    }

    private RegistrationDto toDto(VolunteerRegistration r, Mission m) {
        Association a = associations.findById(m.getAssociationId()).orElseThrow();
        return new RegistrationDto(r.getId(), m.getId(), m.getTitle(), a.getName(), m.getMissionDate(), m.getDurationHours(), r.getUserId(),
                userLookup.name(r.getUserId()), r.getStatus(), r.getReason(), r.getHours(), r.getComment(),
                r.getStatus() == VolunteerStatus.APPROVED && today().isAfter(m.getMissionDate()));
    }
}
