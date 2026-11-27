package fr.epita.platform.apps.skills.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SkillsDtos {

    private SkillsDtos() {
    }

    @Schema(description = "Compétence que je peux enseigner")
    public record Offer(
            @Schema(description = "Compétence du référentiel", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID skillId,
            @Schema(description = "Mon niveau : 3 = confirmé ou 4 = expert (minimum 3 pour être mentor)", example = "4")
            @NotNull @Min(3) @Max(4) Integer level) {
    }

    @Schema(description = "Mon profil de mentor et d'apprenant")
    public record ProfileRequest(
            @Schema(description = "Compétences enseignées, 20 maximum, une fois chacune")
            @NotNull @Size(max = 20) List<@Valid Offer> offers,
            @Schema(description = "Compétences que je veux apprendre, 20 maximum", example = "[\"9b76d11e-ec02-474c-8ca9-8886c4bfbb7d\"]")
            @NotNull @Size(max = 20) List<UUID> wants,
            @Schema(description = "Nombre maximum d'apprenants actifs, de 1 à 5", example = "3")
            @NotNull @Min(1) @Max(5) Integer capacity) {
    }

    @Schema(description = "Demande de mise en relation avec un mentor")
    public record AskRequest(
            @Schema(description = "Mentor sollicité (niveau ≥ 3 sur la compétence, différent de moi)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID mentorId,
            @Schema(description = "Compétence visée", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID skillId,
            @Schema(description = "Message facultatif au mentor, 500 caractères maximum", example = "J'aimerais progresser sur les hooks React.")
            @Size(max = 500) String message) {
    }

    @Schema(description = "Séance d'un binôme")
    public record SessionRequest(
            @Schema(description = "Date de la séance (AAAA-MM-JJ), pas dans le futur", example = "2026-11-20")
            @NotNull @PastOrPresent LocalDate date,
            @Schema(description = "Durée en minutes, de 15 à 480", example = "60")
            @NotNull @Min(15) @Max(480) Integer durationMinutes,
            @Schema(description = "Notes partagées, 2000 caractères maximum", example = "Revue du useEffect et des dépendances.")
            @Size(max = 2000) String notes) {
    }

    @Schema(description = "Ajout d'une compétence au référentiel (ADMIN)")
    public record CatalogRequest(
            @Schema(description = "Nom unique, 60 caractères maximum", example = "TypeScript")
            @NotBlank @Size(max = 60) String name) {
    }

    @Schema(description = "Compétence")
    public record SkillDto(UUID id, String name) {
    }

    @Schema(description = "Compétence enseignée")
    public record OfferDto(UUID skillId, String name, int level) {
    }

    @Schema(description = "Profil")
    public record ProfileDto(UUID userId, List<OfferDto> offers, List<SkillDto> wants, int capacity, long activeLearners) {
    }

    @Schema(description = "Mentor suggéré")
    public record SuggestionDto(UUID mentorId, String mentorName, int level, int remainingPlaces) {
    }

    @Schema(description = "Demande de mise en relation ; EXPIRED = sans réponse depuis 14 jours")
    public record RequestDto(UUID id, UUID learnerId, String learnerName, UUID mentorId, String mentorName, UUID skillId,
                             String skillName, String message, String status, Instant createdAt, Instant decidedAt) {
    }

    @Schema(description = "Séance")
    public record SessionDto(UUID id, LocalDate date, int durationMinutes, String notes, String authorName) {
    }

    @Schema(description = "Binôme")
    public record PairDto(UUID id, UUID mentorId, String mentorName, UUID learnerId, String learnerName, UUID skillId,
                          String skillName, String status, String myRole, int totalMinutes, List<SessionDto> sessions,
                          Instant createdAt) {
    }

    @Schema(description = "Statistiques (ADMIN)")
    public record StatsDto(Map<String, Long> mostWantedSkills, Map<String, Long> mostSolicitedMentors, long activePairs) {
    }
}
