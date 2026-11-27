package fr.epita.platform.apps.santa.dto;

import fr.epita.platform.apps.santa.entity.InvitationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class SantaDtos {

    private SantaDtos() {
    }

    @Schema(description = "Création ou modification d'un événement (modification avant tirage uniquement)")
    public record EventRequest(
            @Schema(description = "Nom, 120 caractères maximum", example = "Secret Santa de l'équipe Front")
            @NotBlank @Size(max = 120) String name,
            @Schema(description = "Date de l'échange (AAAA-MM-JJ), dans le futur", example = "2026-12-19")
            @NotNull LocalDate eventDate,
            @Schema(description = "Budget indicatif par cadeau, en euros, au moins 1", example = "20")
            @NotNull @DecimalMin(value = "1", message = "Le budget doit être positif") BigDecimal budget,
            @Schema(description = "Date limite du tirage (AAAA-MM-JJ), au plus tard le jour de l'événement", example = "2026-12-10")
            @NotNull LocalDate drawDeadline) {
    }

    @Schema(description = "Invitation d'utilisateurs")
    public record InviteRequest(
            @Schema(description = "Utilisateurs à inviter (voir GET /api/santa/candidates)", example = "[\"3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10\"]")
            @NotEmpty List<UUID> userIds) {
    }

    @Schema(description = "Réponse à une invitation")
    public record RespondRequest(
            @Schema(description = "ACCEPTED = je participe, DECLINED = je décline (INVITED est refusé)", example = "ACCEPTED")
            @NotNull InvitationStatus status) {
    }

    @Schema(description = "Exclusion : `giverId` ne doit pas offrir à `receiverId`")
    public record ExclusionDto(
            @Schema(description = "Invité qui offre", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID giverId,
            @Schema(description = "Invité qui ne doit pas lui être attribué (différent de `giverId`)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID receiverId) {
    }

    @Schema(description = "Liste complète des exclusions (remplace la précédente)")
    public record ExclusionsRequest(
            @Schema(description = "Exclusions, liste vide pour tout retirer")
            @NotNull List<@Valid ExclusionDto> exclusions) {
    }

    @Schema(description = "Ma wishlist")
    public record WishlistRequest(
            @Schema(description = "Idées de cadeaux : 20 maximum, chacune de 100 caractères maximum", example = "[\"Un livre de cuisine\", \"Des chaussettes rigolotes\"]")
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> items) {
    }

    public record InvitationDto(UUID userId, String displayName, InvitationStatus status, boolean hasWishlist) {
    }

    public record EventSummaryDto(UUID id, String name, LocalDate eventDate, BigDecimal budget, LocalDate drawDeadline,
                                  String organizerName, boolean isOrganizer, boolean drawDone,
                                  InvitationStatus myStatus, long participants) {
    }

    /** Détail d'un événement : jamais les attributions du tirage. */
    public record EventDto(UUID id, String name, LocalDate eventDate, BigDecimal budget, LocalDate drawDeadline,
                           UUID organizerId, String organizerName, boolean isOrganizer, boolean drawDone,
                           Instant drawnAt, List<InvitationDto> invitations, List<ExclusionDto> exclusions,
                           InvitationStatus myStatus, List<String> myWishlist) {
    }

    public record MyAssignmentDto(UUID eventId, UUID receiverId, String receiverName, List<String> wishlist,
                                  boolean ready, BigDecimal budget, LocalDate eventDate) {
    }

    /** Ce que voit le destinataire : jamais l'identité de son Santa. */
    public record MyGiftDto(UUID eventId, boolean drawDone, boolean ready) {
    }

    public record CandidateDto(UUID id, String displayName) {
    }
}
