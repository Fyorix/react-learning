package fr.epita.platform.apps.farewell.dto;

import fr.epita.platform.apps.farewell.entity.Occasion;
import fr.epita.platform.apps.farewell.entity.PotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FarewellDtos {

    private FarewellDtos() {
    }

    @Schema(description = "Étape 1 : pour qui et pourquoi")
    public record IdentityStep(
            @Schema(description = "Bénéficiaire (ni l'organisateur, ni un invité)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID beneficiaryId,
            @Schema(description = "Occasion : LEAVING, BIRTHDAY, BIRTH ou OTHER", example = "LEAVING")
            @NotNull Occasion occasion,
            @Schema(description = "Titre, 120 caractères maximum", example = "Pot de départ de Léa")
            @NotBlank @Size(max = 120) String title) {
    }

    @Schema(description = "Étape 2 : objectif et date de clôture")
    public record GoalStep(
            @Schema(description = "Objectif en euros (facultatif)", example = "150.00")
            @DecimalMin("1.00") @Digits(integer = 6, fraction = 2) BigDecimal goal,
            @Schema(description = "Clôture automatique (ISO-8601), dans le futur et à 60 jours maximum", example = "2026-12-18T17:00:00Z")
            @NotNull Instant closesAt) {
    }

    @Schema(description = "Étape 3 : collègues invités à participer")
    public record InviteesStep(
            @Schema(description = "Invités (1 à 200), sans le bénéficiaire ni l'organisateur")
            @NotNull @Size(min = 1, max = 200) List<UUID> inviteeIds) {
    }

    @Schema(description = "Ma participation")
    public record ContributionRequest(
            @Schema(description = "Montant en euros, de 1 à 1 000", example = "10.00")
            @NotNull @DecimalMin("1.00") @DecimalMax("1000.00") @Digits(integer = 4, fraction = 2) BigDecimal amount,
            @Schema(description = "Message pour la carte collective, 500 caractères maximum", example = "Bonne continuation, on va te regretter !")
            @Size(max = 500) String message) {
    }

    @Schema(description = "Validité d'une étape du wizard")
    public record StepStatus(int step, String name, boolean valid, List<String> errors) {
    }

    @Schema(description = "Récapitulatif du brouillon (étape 4)")
    public record ReviewDto(PotDto pot, List<StepStatus> steps, boolean canOpen) {
    }

    @Schema(description = "Participant ; `amount` visible de l'organisateur seulement")
    public record ParticipantDto(UUID userId, String name, BigDecimal amount, boolean hasMessage) {
    }

    @Schema(description = """
            Cagnotte. `role` : ORGANIZER, INVITEE, BENEFICIARY ou ADMIN. Les montants individuels (`participants[].amount`) \
            ne sont renseignés que pour l'organisateur.""")
    public record PotDto(UUID id, String title, Occasion occasion, UUID beneficiaryId, String beneficiaryName,
                         UUID organizerId, String organizerName, BigDecimal goal, Instant closesAt, PotStatus status,
                         String role, List<UUID> inviteeIds, int invitees, BigDecimal total, Integer progress,
                         List<ParticipantDto> participants, BigDecimal myAmount, String myMessage) {
    }

    @Schema(description = "Message de la carte collective")
    public record CardMessage(String name, String message, Instant at) {
    }

    @Schema(description = "Carte collective")
    public record CardDto(UUID potId, String title, String beneficiaryName, Occasion occasion, PotStatus status,
                          int participants, List<CardMessage> messages) {
    }
}
