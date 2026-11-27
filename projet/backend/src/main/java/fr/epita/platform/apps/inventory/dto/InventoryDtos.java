package fr.epita.platform.apps.inventory.dto;

import fr.epita.platform.apps.inventory.entity.EquipmentStatus;
import fr.epita.platform.apps.inventory.entity.EquipmentType;
import fr.epita.platform.apps.inventory.entity.IssueStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    @Schema(description = "Ajout ou modification d'un équipement (ADMIN)")
    public record EquipmentRequest(
            @Schema(description = "LAPTOP = PC portable, SCREEN = écran, HEADSET = casque, PHONE = téléphone, OTHER = autre", example = "LAPTOP")
            @NotNull EquipmentType type,
            @Schema(description = "Marque, 60 caractères maximum", example = "Lenovo")
            @NotBlank @Size(max = 60) String brand,
            @Schema(description = "Modèle, 100 caractères maximum", example = "ThinkPad T14")
            @NotBlank @Size(max = 100) String model,
            @Schema(description = "Numéro de série, unique, 100 caractères maximum", example = "PF3XK2ZQ")
            @NotBlank @Size(max = 100) String serialNumber,
            @Schema(description = "Date d'achat (AAAA-MM-JJ), pas dans le futur", example = "2025-09-01")
            @NotNull @PastOrPresent LocalDate purchasedAt) {
    }

    @Schema(description = "Attribution")
    public record AssignRequest(
            @Schema(description = "Utilisateur actif qui reçoit le matériel", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID userId) {
    }

    @Schema(description = "Changement d'état (ADMIN)")
    public record StatusRequest(
            @Schema(description = "Nouvel état : DISPONIBLE = en stock, ATTRIBUE = détenu par un utilisateur, EN_PANNE = en réparation, REBUT = mis au rebut (définitif)", example = "EN_PANNE")
            @NotNull EquipmentStatus status,
            @Schema(description = "Commentaire facultatif, 500 caractères maximum", example = "Écran fissuré, envoyé en réparation")
            @Size(max = 500) String comment) {
    }

    @Schema(description = "Signalement de panne")
    public record IssueRequest(
            @Schema(description = "Description de la panne, 1000 caractères maximum", example = "Le clavier ne répond plus")
            @NotBlank @Size(max = 1000) String description) {
    }

    public record EquipmentDto(UUID id, EquipmentType type, String brand, String model, String serialNumber,
                               LocalDate purchasedAt, EquipmentStatus status, UUID holderId, String holderName,
                               boolean openIssue) {
    }

    public record HolderHistoryDto(UUID userId, String displayName, Instant assignedAt, Instant releasedAt) {
    }

    public record IssueDto(UUID id, UUID equipmentId, String equipmentLabel, String serialNumber, UUID reporterId,
                           String reporterName, String description, IssueStatus status, Instant createdAt,
                           Instant resolvedAt) {
    }

    public record EquipmentDetailDto(EquipmentDto equipment, List<HolderHistoryDto> history, List<IssueDto> issues) {
    }
}
