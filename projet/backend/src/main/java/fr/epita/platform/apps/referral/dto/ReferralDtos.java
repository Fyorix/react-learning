package fr.epita.platform.apps.referral.dto;

import fr.epita.platform.apps.referral.entity.ReferralStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ReferralDtos {

    private ReferralDtos() {
    }

    @Schema(description = "Poste ouvert à la cooptation (ADMIN)")
    public record JobRequest(
            @Schema(description = "Intitulé, 120 caractères maximum", example = "Développeur·se Java")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Équipe, 80 caractères maximum", example = "Plateforme")
            @NotBlank @Size(max = 80) String team,
            @Schema(description = "Description, 4 000 caractères maximum", example = "Microservices Spring Boot…")
            @NotBlank @Size(max = 4000) String description,
            @Schema(description = "Prime de cooptation en euros", example = "1500.00")
            @NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal bonusAmount,
            @Schema(description = "Ouvert aux recommandations", example = "true")
            @NotNull Boolean open) {
    }

    @Schema(description = "Recommandation d'un candidat")
    public record ReferralRequest(
            @Schema(description = "Poste ouvert", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID jobId,
            @Schema(description = "Nom du candidat, 120 caractères maximum", example = "Karim Diallo")
            @NotBlank @Size(max = 120) String candidateName,
            @Schema(description = "E-mail du candidat (pas le vôtre)", example = "karim.diallo@example.com")
            @NotBlank @Email @Size(max = 160) String candidateEmail,
            @Schema(description = "Profil LinkedIn (facultatif)", example = "https://www.linkedin.com/in/karim-diallo")
            @Pattern(regexp = "https://([a-z]{2,3}\\.)?linkedin\\.com/.{1,250}") String linkedinUrl,
            @Schema(description = "Pourquoi cette personne, 2 000 caractères maximum", example = "Nous avons travaillé ensemble 3 ans chez…")
            @NotBlank @Size(max = 2000) String message) {
    }

    @Schema(description = "Changement de statut (ADMIN)")
    public record StatusRequest(
            @Schema(description = "Statut cible : étape suivante (IN_REVIEW, HIRED, TRIAL_VALIDATED) ou REJECTED", example = "IN_REVIEW")
            @NotNull ReferralStatus status,
            @Schema(description = "Commentaire interne RH, jamais visible du coopteur", example = "Entretien prévu le 12")
            @Size(max = 2000) String internalComment) {
    }

    @Schema(description = "Poste")
    public record JobDto(UUID id, String title, String team, String description, BigDecimal bonusAmount, boolean open, long referrals) {
    }

    @Schema(description = "Ma recommandation (sans commentaire interne)")
    public record MyReferralDto(UUID id, UUID jobId, String jobTitle, BigDecimal bonusAmount, String candidateName, String candidateEmail,
                                ReferralStatus status, Instant statusChangedAt, Instant createdAt, Instant bonusPaidAt) {
    }

    @Schema(description = "Recommandation (vue RH)")
    public record ReferralDto(UUID id, UUID jobId, String jobTitle, BigDecimal bonusAmount, UUID referrerId, String referrerName,
                              boolean referrerActive, String candidateName, String candidateEmail, String linkedinUrl, String message,
                              ReferralStatus status, String internalComment, Instant statusChangedAt, Instant createdAt, Instant bonusPaidAt) {
    }
}
