package fr.epita.platform.apps.pomodoro.dto;

import fr.epita.platform.apps.pomodoro.entity.Phase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PomodoroDtos {

    private PomodoroDtos() {
    }

    @Schema(description = "Création ou réglage d'une salle de focus (durées modifiables seulement quand la salle est IDLE)")
    public record RoomRequest(
            @Schema(description = "Nom de la salle, 80 caractères maximum", example = "Focus équipe Front")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Durée d'un focus en minutes, de 1 à 90 (25 par défaut)", example = "25")
            @Min(1) @Max(90) Integer focusMinutes,
            @Schema(description = "Pause courte en minutes, de 1 à 30 (5 par défaut)", example = "5")
            @Min(1) @Max(30) Integer shortBreakMinutes,
            @Schema(description = "Pause longue en minutes, de 1 à 60 (15 par défaut)", example = "15")
            @Min(1) @Max(60) Integer longBreakMinutes,
            @Schema(description = "Nombre de focus avant une pause longue, de 2 à 8 (4 par défaut)", example = "4")
            @Min(2) @Max(8) Integer cyclesBeforeLongBreak) {
    }

    @Schema(description = "Invitation de membres")
    public record InviteRequest(
            @Schema(description = "Utilisateurs à ajouter à la salle", example = "[\"9b76d11e-ec02-474c-8ca9-8886c4bfbb7d\"]")
            @NotEmpty @Size(max = 30) List<UUID> userIds) {
    }

    @Schema(description = "Membre et sa présence (AWAY après 60 s sans signal)")
    public record MemberDto(UUID userId, String displayName, boolean host, String presence) {
    }

    @Schema(description = """
            État de la salle. Le front calcule le temps restant à partir de `phaseEndsAt` corrigé du décalage \
            `serverTime` − heure locale ; en pause, `remainingSeconds` est figé.""")
    public record StateDto(UUID roomId, String name, UUID hostId, Phase phase, Phase pausedPhase, Instant phaseEndsAt,
                           Long remainingSeconds, Instant serverTime, int cycle, int cyclesBeforeLongBreak,
                           int focusMinutes, int shortBreakMinutes, int longBreakMinutes, List<MemberDto> members,
                           boolean isHost) {
    }

    @Schema(description = "Salle dans une liste")
    public record RoomSummaryDto(UUID id, String name, String hostName, Phase phase, int memberCount) {
    }

    @Schema(description = "Pomodoros terminés pour un jour")
    public record DayCountDto(LocalDate date, long count) {
    }

    @Schema(description = "Mes statistiques")
    public record StatsDto(String period, long total, List<DayCountDto> days) {
    }
}
