package fr.epita.platform.apps.association.dto;

import fr.epita.platform.apps.association.entity.AssemblyStatus;
import fr.epita.platform.apps.association.entity.AssociationRegistrationStatus;
import fr.epita.platform.apps.association.entity.FeeCategory;
import fr.epita.platform.apps.association.entity.VoteChoice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AssociationDtos {

    private AssociationDtos() {
    }

    @Schema(description = "Cotisation (BOARD) ; saison AAAA-AAAA (1er septembre → 31 août), saison en cours par défaut")
    public record FeeRequest(
            @Schema(description = "Saison, ex. 2026-2027 (facultative)", example = "2026-2027")
            @Pattern(regexp = "\\d{4}-\\d{4}") String season,
            @Schema(description = "STUDENT, FULL ou SUPPORTER", example = "FULL")
            @NotNull FeeCategory category,
            @Schema(description = "Montant en euros, supérieur à 0", example = "30.00")
            @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal amount,
            @Schema(description = "Date de paiement, pas dans le futur", example = "2026-09-15")
            @NotNull LocalDate paidAt) {
    }

    @Schema(description = "Activité (BOARD)")
    public record ActivityRequest(
            @Schema(description = "Titre, 120 caractères maximum", example = "Sortie escalade")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Date, dans le futur", example = "2026-12-12T14:00:00+01:00")
            @NotNull Instant date,
            @Schema(description = "Capacité, de 1 à 1 000", example = "20")
            @Min(1) @Max(1000) int capacity,
            @Schema(description = "Réservée aux adhérents à jour", example = "true")
            boolean membersOnly) {
    }

    @Schema(description = "Convocation d'une assemblée générale (BOARD)")
    public record AssemblyRequest(
            @Schema(description = "Titre, 120 caractères maximum", example = "AG ordinaire 2026")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Date de l'AG", example = "2026-12-15T18:30:00+01:00")
            @NotNull Instant date,
            @Schema(description = "Quorum en pourcentage des adhérents à jour, de 1 à 100", example = "25")
            @Min(1) @Max(100) int quorumPercent,
            @Schema(description = "Résolutions soumises au vote (1 à 20)")
            @NotEmpty @Size(max = 20) List<@Valid ResolutionInput> resolutions) {
    }

    @Schema(description = "Résolution")
    public record ResolutionInput(
            @Schema(description = "Titre, 120 caractères maximum", example = "Approbation des comptes")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Texte, 2 000 caractères maximum", example = "L'AG approuve les comptes de l'exercice écoulé.")
            @NotBlank @Size(max = 2000) String text) {
    }

    @Schema(description = "Mon vote (une voix par résolution, définitive)")
    public record VotesRequest(
            @Schema(description = "Choix par résolution")
            @NotEmpty @Size(max = 20) List<@Valid VoteInput> votes) {
    }

    @Schema(description = "Choix sur une résolution")
    public record VoteInput(
            @Schema(description = "Résolution de l'AG", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID resolutionId,
            @Schema(description = "FOR, AGAINST ou ABSTAIN", example = "FOR")
            @NotNull VoteChoice choice) {
    }

    @Schema(description = "Cotisation")
    public record FeeDto(UUID id, String season, FeeCategory category, BigDecimal amount, LocalDate paidAt, String recordedByName) {
    }

    @Schema(description = "Adhérent")
    public record MemberDto(UUID userId, String name, String email, boolean upToDate, String lastSeason, FeeCategory lastCategory) {
    }

    @Schema(description = "Mon adhésion")
    public record MyMembershipDto(String currentSeason, boolean upToDate, boolean board, List<FeeDto> fees) {
    }

    @Schema(description = "Activité ; `myStatus` nul si non inscrit")
    public record ActivityDto(UUID id, String title, Instant date, int capacity, boolean membersOnly, long confirmed, long waitlisted,
                              AssociationRegistrationStatus myStatus, Integer myPosition, boolean open) {
    }

    @Schema(description = "Résolution ; décompte visible après clôture")
    public record ResolutionDto(UUID id, String title, String text, boolean votedByMe, Integer forCount, Integer againstCount, Integer abstainCount, Boolean adopted) {
    }

    @Schema(description = "Assemblée générale")
    public record AssemblyDto(UUID id, String title, Instant date, int quorumPercent, AssemblyStatus status, boolean canVote, long voters,
                              Integer eligible, Boolean quorumReached, List<ResolutionDto> resolutions) {
    }
}
