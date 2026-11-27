package fr.epita.platform.apps.oncall.dto;

import fr.epita.platform.apps.oncall.entity.SwapStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OnCallDtos {

    private OnCallDtos() {
    }

    private static final String WEEK = "\\d{4}-W\\d{2}";

    @Schema(description = "Équipe d'astreinte (ADMIN)")
    public record TeamRequest(
            @Schema(description = "Nom unique, 80 caractères maximum", example = "Exploitation")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Manager (valide la rotation et les échanges ; pas forcément membre)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID managerId,
            @Schema(description = "Membres de la rotation, au moins 2")
            @NotNull @Size(min = 2, max = 52) List<UUID> memberIds) {
    }

    @Schema(description = "Génération de la rotation")
    public record GenerateRequest(
            @Schema(description = "Première semaine (ISO AAAA-Www), à venir", example = "2026-W50")
            @NotNull @Pattern(regexp = WEEK) String fromWeek,
            @Schema(description = "Nombre de semaines, de 1 à 52", example = "8")
            @Min(1) @Max(52) int weeks,
            @Schema(description = "Remplacer les affectations déjà présentes (sinon elles sont conservées)", example = "false")
            boolean overwrite) {
    }

    @Schema(description = "Affectation manuelle d'une semaine")
    public record AssignRequest(
            @Schema(description = "Membre d'astreinte", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID userId) {
    }

    @Schema(description = "Indisponibilité déclarée par un membre")
    public record UnavailabilityRequest(
            @Schema(description = "Lundi de la semaine concernée, à venir", example = "2026-12-21")
            @NotNull LocalDate weekStart,
            @Schema(description = "Motif, 200 caractères maximum", example = "Congés de fin d'année")
            @NotBlank @Size(max = 200) String reason) {
    }

    @Schema(description = "Demande d'échange de semaines")
    public record SwapRequest(
            @Schema(description = "Équipe", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID teamId,
            @Schema(description = "Ma semaine d'astreinte (ISO AAAA-Www)", example = "2026-W51")
            @NotNull @Pattern(regexp = WEEK) String myWeek,
            @Schema(description = "Semaine du collègue (ISO AAAA-Www)", example = "2026-W52")
            @NotNull @Pattern(regexp = WEEK) String otherWeek,
            @Schema(description = "Collègue titulaire de `otherWeek`", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID otherUserId) {
    }

    @Schema(description = "Membre")
    public record MemberDto(UUID userId, String name) {
    }

    @Schema(description = "Équipe ; `manager` = l'utilisateur courant en est le manager")
    public record TeamDto(UUID id, String name, UUID managerId, String managerName, List<MemberDto> members, boolean manager, boolean member) {
    }

    @Schema(description = "Semaine du planning ; `locked` = passée ou en cours, plus modifiable")
    public record WeekDto(String week, LocalDate weekStart, UUID userId, String name, boolean locked, boolean mine, List<String> unavailable) {
    }

    @Schema(description = "Planning d'une équipe")
    public record ScheduleDto(UUID teamId, String teamName, List<WeekDto> weeks) {
    }

    @Schema(description = "Une de mes astreintes")
    public record MyShiftDto(UUID teamId, String teamName, String week, LocalDate weekStart, boolean locked) {
    }

    @Schema(description = "Échange")
    public record SwapDto(UUID id, UUID teamId, String teamName, UUID requesterId, String requesterName, String myWeek,
                          UUID otherUserId, String otherUserName, String otherWeek, SwapStatus status, String decidedByName,
                          boolean canAccept, boolean canApprove, boolean canReject) {
    }

    @Schema(description = "Nombre d'astreintes d'un membre sur l'année")
    public record StatDto(UUID userId, String name, long weeks) {
    }
}
