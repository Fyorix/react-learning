package fr.epita.platform.apps.poll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class PollDtos {

    private PollDtos() {
    }

    @Schema(description = "Création ou modification d'un sondage (modification impossible dès le premier vote)")
    public record PollRequest(
            @Schema(description = "Question posée, 200 caractères maximum", example = "Où organiser le séminaire ?")
            @NotBlank @Size(max = 200) String question,
            @Schema(description = "Libellés des options : entre 2 et 10, chacun de 100 caractères maximum", example = "[\"Lyon\", \"Bordeaux\", \"Lille\"]")
            @NotNull @Size(min = 2, max = 10, message = "Il faut entre 2 et 10 options") List<@NotBlank @Size(max = 100) String> options,
            @Schema(description = "true : plusieurs options peuvent être cochées ; false : une seule", example = "false")
            boolean multipleChoice,
            @Schema(description = "true : résultats visibles avant d'avoir voté ; false : seulement après", example = "false")
            boolean resultsVisibleBeforeVote,
            @Schema(description = "Date de clôture automatique facultative (ISO 8601, UTC), dans le futur", example = "2026-12-15T18:00:00Z")
            Instant closesAt) {
    }

    @Schema(description = "Vote : identifiants des options choisies")
    public record VoteRequest(
            @Schema(description = "Options choisies (au moins une ; exactement une si le sondage est à choix unique)", example = "[\"3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10\"]")
            @NotEmpty List<UUID> optionIds) {
    }

    public record OptionDto(UUID id, String label) {
    }

    public record PollSummaryDto(UUID id, String question, UUID creatorId, String creatorName, boolean open,
                                 Instant closesAt, boolean multipleChoice, long totalVoters, boolean hasVoted,
                                 Instant createdAt) {
    }

    public record PollDto(UUID id, String question, UUID creatorId, String creatorName, boolean multipleChoice,
                          boolean resultsVisibleBeforeVote, Instant closesAt, Instant closedAt, boolean open,
                          List<OptionDto> options, List<UUID> myVote, long totalVoters, boolean canManage,
                          boolean canSeeResults, Instant createdAt) {
    }

    public record OptionResultDto(UUID id, String label, long votes, double percentage) {
    }

    public record PollResultsDto(UUID pollId, String question, boolean open, long totalVoters,
                                 List<OptionResultDto> options) {
    }
}
