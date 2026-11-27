package fr.epita.platform.apps.mood.dto;

import fr.epita.platform.apps.mood.entity.MoodEntry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

public final class MoodDtos {

    private MoodDtos() {
    }

    @Schema(description = "Saisie de l'humeur du jour. Une seule entrée par jour et par utilisateur.")
    public record MoodEntryRequest(
            @Schema(description = "Humeur de 1 (très mauvaise) à 5 (excellente)", example = "4")
            @NotNull @Min(1) @Max(5) Integer score,
            @Schema(description = "Commentaire facultatif, 500 caractères maximum", example = "Bonne journée, sprint terminé")
            @Size(max = 500) String comment,
            @Schema(description = "Étiquettes facultatives : 10 maximum, chacune de 1 à 30 caractères", example = "[\"travail\", \"sport\"]")
            @Size(max = 10) List<@Size(min = 1, max = 30) String> tags) {
    }

    public record MoodEntryDto(LocalDate date, int score, String comment, List<String> tags) {
        public static MoodEntryDto of(MoodEntry e) {
            return new MoodEntryDto(e.getDate(), e.getScore(), e.getComment(), e.getTags().stream().sorted().toList());
        }
    }

    public record MoodStatsDto(String period, LocalDate from, LocalDate to, int count, Double average,
                               Map<Integer, Long> distribution, int currentStreak, int longestStreak) {
    }

    public record DailyAverage(LocalDate date, double average, long count) {
    }

    /** Statistiques globales anonymisées : aucun identifiant d'utilisateur. */
    public record AdminMoodStatsDto(LocalDate from, LocalDate to, long totalEntries, long participants,
                                    Double average, Map<Integer, Long> distribution,
                                    List<DailyAverage> dailyAverages) {
    }
}
