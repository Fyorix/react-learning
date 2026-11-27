package fr.epita.platform.apps.birthday.dto;

import fr.epita.platform.apps.birthday.entity.EventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BirthdayDtos {

    private BirthdayDtos() {
    }

    @Schema(description = "Fiche d'un salarié (ADMIN)")
    public record PersonRequest(
            @Schema(description = "Date de naissance (facultative, passée)", example = "1990-02-29")
            LocalDate birthDate,
            @Schema(description = "Date d'arrivée, pas dans le futur", example = "2021-12-01")
            @NotNull LocalDate hireDate,
            @Schema(description = "Équipe", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            UUID teamId) {
    }

    @Schema(description = "Préférences")
    public record PreferencesRequest(
            @Schema(description = "Afficher mon anniversaire de naissance (l'ancienneté reste visible)", example = "true")
            @NotNull Boolean showBirthday) {
    }

    @Schema(description = "Nouvelle équipe (ADMIN)")
    public record TeamRequest(
            @Schema(description = "Nom unique, 80 caractères maximum", example = "Marketing")
            @NotBlank @Size(max = 80) String name) {
    }

    @Schema(description = """
            Événement : BIRTHDAY (naissance, jamais d'année ni d'âge) ou ANNIVERSARY (ancienneté, `years` ans). \
            `milestone` : jalon mis en avant (1, 3, 5, 10, 15, 20 ans…).""")
    public record EventDto(LocalDate date, UUID userId, String displayName, String teamName, EventType type, Integer years, boolean milestone) {
    }

    @Schema(description = "Rappels : événements du jour et du lendemain (fuseau Europe/Paris)")
    public record RemindersDto(LocalDate today, List<EventDto> todayEvents, List<EventDto> tomorrowEvents) {
    }

    @Schema(description = "Ma fiche (avec mon année de naissance, pour moi seul)")
    public record MeDto(UUID userId, String displayName, LocalDate birthDate, LocalDate hireDate, UUID teamId, String teamName, boolean showBirthday, boolean exists) {
    }

    @Schema(description = "Fiche vue par un ADMIN (jour et mois de naissance uniquement)")
    public record PersonDto(UUID userId, String displayName, String birthDay, LocalDate hireDate, UUID teamId, String teamName, boolean showBirthday) {
    }

    @Schema(description = "Équipe")
    public record TeamDto(UUID id, String name, long members) {
    }

    @Schema(description = "Résultat d'une ligne d'import")
    public record ImportLine(int line, String email, boolean ok, String message) {
    }

    @Schema(description = "Rapport d'import : lignes valides importées, lignes invalides signalées")
    public record ImportReport(int imported, int rejected, List<ImportLine> lines) {
    }
}
