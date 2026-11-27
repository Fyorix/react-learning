package fr.epita.platform.apps.ticketing.dto;

import fr.epita.platform.apps.ticketing.entity.TicketOrderStatus;
import fr.epita.platform.apps.ticketing.entity.EventTicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TicketingDtos {

    private TicketingDtos() {
    }

    @Schema(description = "Événement : la somme des quotas ne dépasse pas la capacité ; fin des ventes au plus tard au début")
    public record EventRequest(
            @Schema(description = "Titre, 120 caractères maximum", example = "Concert de fin d'année")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Lieu, 120 caractères maximum", example = "Amphi 4")
            @NotBlank @Size(max = 120) String venue,
            @Schema(description = "Début (ISO-8601), dans le futur", example = "2026-12-18T20:00:00+01:00")
            @NotNull Instant startsAt,
            @Schema(description = "Jauge totale, de 1 à 100 000", example = "300")
            @Min(1) @Max(100000) int capacity,
            @Schema(description = "Fin des ventes, au plus tard au début", example = "2026-12-18T18:00:00+01:00")
            @NotNull Instant salesEndAt,
            @Schema(description = "Tarifs (1 à 10)")
            @NotEmpty @Size(max = 10) List<@Valid RateInput> rates) {
    }

    @Schema(description = "Tarif ; `id` d'un tarif existant pour le modifier, absent pour en créer un")
    public record RateInput(
            @Schema(description = "Tarif existant (modification)", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            UUID id,
            @Schema(description = "Libellé, 60 caractères maximum", example = "Étudiant")
            @NotBlank @Size(max = 60) String label,
            @Schema(description = "Prix en euros, 0 pour gratuit", example = "8.00")
            @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal price,
            @Schema(description = "Quota de billets, jamais sous le nombre vendu", example = "200")
            @Min(0) @Max(100000) int quota) {
    }

    @Schema(description = "Commande : 6 billets maximum")
    public record OrderRequest(
            @Schema(description = "Lignes de commande")
            @NotEmpty @Size(max = 10) List<@Valid OrderLine> lines) {
    }

    @Schema(description = "Ligne de commande")
    public record OrderLine(
            @Schema(description = "Tarif de l'événement", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID rateId,
            @Schema(description = "Quantité, de 1 à 6", example = "2")
            @Min(1) @Max(6) int quantity) {
    }

    @Schema(description = "Contrôle d'un billet")
    public record ScanRequest(
            @Schema(description = "Code du billet (QR code)", example = "6f1c0a3e-1b2d-4c5e-9f80-0a1b2c3d4e5f-1A2B3C4D")
            @NotBlank @Size(max = 60) String code) {
    }

    @Schema(description = "Tarif et places restantes")
    public record RateDto(UUID id, String label, BigDecimal price, int quota, long sold, long remaining) {
    }

    @Schema(description = "Événement")
    public record EventDto(UUID id, String title, String venue, Instant startsAt, int capacity, Instant salesEndAt, String organizerName,
                           boolean organizer, boolean onSale, long sold, long remaining, List<RateDto> rates) {
    }

    @Schema(description = "Billet")
    public record TicketDto(UUID id, String code, String rateLabel, BigDecimal price, EventTicketStatus status, Instant scannedAt) {
    }

    @Schema(description = "Commande et billets ; `refundable` : remboursable maintenant")
    public record OrderDto(UUID id, UUID eventId, String eventTitle, Instant eventStartsAt, String venue, TicketOrderStatus status,
                           BigDecimal total, Instant createdAt, boolean refundable, List<TicketDto> tickets) {
    }

    @Schema(description = "Résultat du contrôle : billet valide, entrée enregistrée")
    public record ScanResult(String code, String holderName, String rateLabel, Instant scannedAt) {
    }

    @Schema(description = "Ventes d'un tarif")
    public record RateStats(UUID rateId, String label, int quota, long sold, BigDecimal revenue, long entered) {
    }

    @Schema(description = "Tableau de bord organisateur")
    public record DashboardDto(UUID eventId, String title, int capacity, long sold, int fillPercent, BigDecimal revenue, long entered,
                               long refundedOrders, List<RateStats> rates) {
    }
}
