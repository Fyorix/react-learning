package fr.epita.platform.apps.inventory.service;

import fr.epita.platform.apps.inventory.dto.InventoryDtos.EquipmentDetailDto;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.EquipmentDto;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.EquipmentRequest;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.HolderHistoryDto;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.IssueDto;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.StatusRequest;
import fr.epita.platform.apps.inventory.entity.Equipment;
import fr.epita.platform.apps.inventory.entity.EquipmentAssignment;
import fr.epita.platform.apps.inventory.entity.EquipmentStatus;
import fr.epita.platform.apps.inventory.entity.EquipmentType;
import fr.epita.platform.apps.inventory.entity.Issue;
import fr.epita.platform.apps.inventory.entity.IssueStatus;
import fr.epita.platform.apps.inventory.repository.EquipmentAssignmentRepository;
import fr.epita.platform.apps.inventory.repository.EquipmentRepository;
import fr.epita.platform.apps.inventory.repository.IssueRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.service.UserDeletionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Inventaire. Fournit aussi le contrôle §5.11 qui s'ajoute au §4.4 :
 * un utilisateur qui détient du matériel ne peut être ni supprimé ni désactivé.
 */
@Service
@RequiredArgsConstructor
public class InventoryService implements UserDeletionGuard {

    private final EquipmentRepository equipment;
    private final EquipmentAssignmentRepository history;
    private final IssueRepository issues;
    private final UserLookup userLookup;
    private final Clock clock;

    @Override
    public void beforeRemoval(User user) {
        if (equipment.existsByHolderId(user.getId())) {
            throw ApiException.conflict("USER_HOLDS_EQUIPMENT",
                    "Cet utilisateur détient du matériel : faites-le restituer d'abord");
        }
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public PageResponse<EquipmentDto> list(EquipmentType type, EquipmentStatus status, String search, int page, int size) {
        String term = search == null ? "" : search.trim().toLowerCase();
        List<Equipment> list = equipment.findAllByOrderByTypeAscBrandAscModelAsc().stream()
                .filter(e -> type == null || e.getType() == type)
                .filter(e -> status == null || e.getStatus() == status)
                .filter(e -> term.isEmpty() || (e.getBrand() + " " + e.getModel() + " " + e.getSerialNumber())
                        .toLowerCase().contains(term))
                .toList();
        return PageResponse.ofList(toDtos(list), page, size);
    }

    @Transactional(readOnly = true)
    public EquipmentDetailDto detail(UUID id) {
        Equipment e = find(id);
        List<EquipmentAssignment> hist = history.findByEquipmentIdOrderByAssignedAtDesc(id);
        Map<UUID, String> names = userLookup.namesOf(hist, EquipmentAssignment::getUserId);
        return new EquipmentDetailDto(toDtos(List.of(e)).getFirst(),
                hist.stream().map(h -> new HolderHistoryDto(h.getUserId(), names.get(h.getUserId()), h.getAssignedAt(),
                        h.getReleasedAt())).toList(),
                toIssueDtos(issues.findByEquipmentIdOrderByCreatedAtDesc(id)));
    }

    @Transactional
    public EquipmentDto create(EquipmentRequest req) {
        if (equipment.existsBySerialNumberIgnoreCase(req.serialNumber().trim())) {
            throw ApiException.conflict("SERIAL_NUMBER_ALREADY_USED", "Ce numéro de série existe déjà");
        }
        Equipment e = new Equipment();
        apply(e, req);
        return toDtos(List.of(equipment.saveAndFlush(e))).getFirst();
    }

    @Transactional
    public EquipmentDto update(UUID id, EquipmentRequest req) {
        Equipment e = find(id);
        if (!e.getSerialNumber().equalsIgnoreCase(req.serialNumber().trim())
                && equipment.existsBySerialNumberIgnoreCase(req.serialNumber().trim())) {
            throw ApiException.conflict("SERIAL_NUMBER_ALREADY_USED", "Ce numéro de série existe déjà");
        }
        apply(e, req);
        return toDtos(List.of(equipment.saveAndFlush(e))).getFirst();
    }

    /** Seul un équipement DISPONIBLE peut être attribué ; un seul détenteur à la fois. */
    @Transactional
    public EquipmentDto assign(UUID id, UUID userId) {
        Equipment e = equipment.lockById(id).orElseThrow(() -> ApiException.notFound("Équipement"));
        if (e.getStatus() != EquipmentStatus.DISPONIBLE) {
            throw ApiException.conflict("EQUIPMENT_NOT_AVAILABLE", "Seul un équipement disponible peut être attribué");
        }
        User user = userLookup.require(userId);
        if (!user.isEnabled()) {
            throw ApiException.conflict("USER_DISABLED", "Impossible d'attribuer du matériel à un compte désactivé");
        }
        e.setStatus(EquipmentStatus.ATTRIBUE);
        e.setHolderId(userId);
        EquipmentAssignment a = new EquipmentAssignment();
        a.setEquipmentId(id);
        a.setUserId(userId);
        a.setAssignedAt(clock.instant());
        history.save(a);
        equipment.saveAndFlush(e);
        return toDtos(List.of(e)).getFirst();
    }

    @Transactional
    public EquipmentDto release(UUID id) {
        Equipment e = equipment.lockById(id).orElseThrow(() -> ApiException.notFound("Équipement"));
        if (e.getStatus() != EquipmentStatus.ATTRIBUE) {
            throw ApiException.conflict("EQUIPMENT_NOT_ASSIGNED", "L'équipement n'est attribué à personne");
        }
        releaseInternal(e);
        e.setStatus(EquipmentStatus.DISPONIBLE);
        equipment.saveAndFlush(e);
        return toDtos(List.of(e)).getFirst();
    }

    /**
     * EN_PANNE : restitution automatique ; DISPONIBLE ou REBUT : signalements ouverts clôturés.
     * REBUT est définitif ; ATTRIBUE passe par /assign.
     */
    @Transactional
    public EquipmentDto changeStatus(UUID id, StatusRequest req) {
        Equipment e = equipment.lockById(id).orElseThrow(() -> ApiException.notFound("Équipement"));
        if (e.getStatus() == EquipmentStatus.REBUT) {
            throw ApiException.conflict("EQUIPMENT_SCRAPPED", "Un équipement au rebut ne change plus d'état");
        }
        if (req.status() == EquipmentStatus.ATTRIBUE) {
            throw ValidationException.field("status", "Utilisez l'attribution pour passer un équipement en ATTRIBUE");
        }
        if (e.getStatus() == EquipmentStatus.ATTRIBUE) {
            releaseInternal(e);
        }
        if (req.status() == EquipmentStatus.DISPONIBLE || req.status() == EquipmentStatus.REBUT) {
            Instant now = clock.instant();
            issues.findByEquipmentIdAndStatus(id, IssueStatus.OPEN).forEach(i -> {
                i.setStatus(IssueStatus.RESOLVED);
                i.setResolvedAt(now);
            });
        }
        e.setStatus(req.status());
        equipment.saveAndFlush(e);
        return toDtos(List.of(e)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<IssueDto> adminIssues(String status) {
        List<Issue> list = switch (status == null ? "open" : status) {
            case "open" -> issues.findByStatusOrderByCreatedAtAsc(IssueStatus.OPEN);
            case "resolved" -> issues.findByStatusOrderByCreatedAtAsc(IssueStatus.RESOLVED);
            case "all" -> issues.findAllByOrderByCreatedAtDesc();
            default -> throw ValidationException.field("status", "Valeurs possibles : open, resolved, all");
        };
        return toIssueDtos(list);
    }

    // ---------- utilisateur ----------

    @Transactional(readOnly = true)
    public List<EquipmentDto> mine(AuthUser me) {
        return toDtos(equipment.findByHolderIdOrderByTypeAsc(me.id()));
    }

    /** Seul le détenteur actuel peut signaler une panne. */
    @Transactional
    public IssueDto report(AuthUser me, UUID id, String description) {
        Equipment e = find(id);
        if (!me.id().equals(e.getHolderId())) {
            throw ApiException.forbidden("Seul le détenteur actuel peut signaler une panne");
        }
        if (!issues.findByEquipmentIdAndStatus(id, IssueStatus.OPEN).isEmpty()) {
            throw ApiException.conflict("ISSUE_ALREADY_OPEN", "Une panne est déjà signalée sur cet équipement");
        }
        Issue issue = new Issue();
        issue.setEquipmentId(id);
        issue.setReporterId(me.id());
        issue.setDescription(description.trim());
        issue.setCreatedAt(clock.instant());
        return toIssueDtos(List.of(issues.saveAndFlush(issue))).getFirst();
    }

    // ---------- interne ----------

    private void releaseInternal(Equipment e) {
        history.findByEquipmentIdAndReleasedAtIsNull(e.getId()).ifPresent(a -> a.setReleasedAt(clock.instant()));
        e.setHolderId(null);
    }

    private void apply(Equipment e, EquipmentRequest req) {
        e.setType(req.type());
        e.setBrand(req.brand().trim());
        e.setModel(req.model().trim());
        e.setSerialNumber(req.serialNumber().trim());
        e.setPurchasedAt(req.purchasedAt());
    }

    private Equipment find(UUID id) {
        return equipment.findById(id).orElseThrow(() -> ApiException.notFound("Équipement"));
    }

    private List<EquipmentDto> toDtos(List<Equipment> list) {
        Map<UUID, String> names = userLookup.namesOf(list, Equipment::getHolderId);
        Set<UUID> withOpenIssue = issues.findByStatusOrderByCreatedAtAsc(IssueStatus.OPEN).stream()
                .map(Issue::getEquipmentId).collect(Collectors.toSet());
        return list.stream().map(e -> new EquipmentDto(e.getId(), e.getType(), e.getBrand(), e.getModel(),
                e.getSerialNumber(), e.getPurchasedAt(), e.getStatus(), e.getHolderId(),
                e.getHolderId() == null ? null : names.get(e.getHolderId()), withOpenIssue.contains(e.getId()))).toList();
    }

    private List<IssueDto> toIssueDtos(List<Issue> list) {
        Map<UUID, Equipment> eq = equipment.findAllById(list.stream().map(Issue::getEquipmentId).distinct().toList())
                .stream().collect(Collectors.toMap(Equipment::getId, e -> e));
        Map<UUID, String> names = userLookup.namesOf(list, Issue::getReporterId);
        return list.stream().map(i -> {
            Equipment e = eq.get(i.getEquipmentId());
            return new IssueDto(i.getId(), i.getEquipmentId(), e.getBrand() + " " + e.getModel(), e.getSerialNumber(),
                    i.getReporterId(), names.get(i.getReporterId()), i.getDescription(), i.getStatus(),
                    i.getCreatedAt(), i.getResolvedAt());
        }).toList();
    }
}
