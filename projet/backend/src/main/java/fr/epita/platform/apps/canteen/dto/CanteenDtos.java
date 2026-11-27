package fr.epita.platform.apps.canteen.dto;

import fr.epita.platform.apps.canteen.entity.DishCategory;
import fr.epita.platform.apps.canteen.entity.WeekStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class CanteenDtos {

    private CanteenDtos() {
    }

    @Schema(description = "Ajout ou modification d'un plat (ADMIN)")
    public record DishRequest(
            @Schema(description = "Nom, unique dans sa catégorie, 120 caractères maximum", example = "Velouté de potiron")
            @NotBlank @Size(max = 120) String name,
            @Schema(description = "STARTER = entrée, MAIN = plat principal, DESSERT = dessert", example = "STARTER")
            @NotNull DishCategory category,
            @Schema(description = "Description facultative, 500 caractères maximum", example = "Potiron, crème, croûtons")
            @Size(max = 500) String description,
            @Schema(description = "true si le plat est végétarien", example = "true")
            boolean vegetarian,
            @Schema(description = "Allergènes facultatifs : 10 maximum, chacun de 30 caractères maximum", example = "[\"lait\", \"gluten\"]")
            @Size(max = 10) List<@NotBlank @Size(max = 30) String> allergens) {
    }

    @Schema(description = "Menu d'un jour : une entrée, un plat, un dessert")
    public record DayMenuRequest(
            @Schema(description = "Entrée (plat de catégorie STARTER, non archivé)", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID starterId,
            @Schema(description = "Plat principal (catégorie MAIN, non archivé)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID mainId,
            @Schema(description = "Dessert (catégorie DESSERT, non archivé)", example = "0fee6165-1706-4ef5-9936-e2309e2f481c")
            @NotNull UUID dessertId) {
    }

    @Schema(description = "Options de génération aléatoire (corps facultatif)")
    public record GenerateRequest(
            @Schema(description = "true : regénère toute la semaine ; false ou absent : remplit seulement les jours vides", example = "false")
            Boolean overwrite,
            @Schema(description = "Nombre minimum de jours (0 à 5) avec un plat principal végétarien", example = "2")
            @Min(0) @Max(5) Integer vegetarianDays) {
    }

    public record DishDto(UUID id, String name, DishCategory category, String description, boolean vegetarian,
                          List<String> allergens, boolean archived) {
    }

    public record DayMenuDto(DayOfWeek dayOfWeek, LocalDate date, DishDto starter, DishDto main, DishDto dessert,
                             boolean complete) {
    }

    /** {@code persisted = false} : planning pas encore créé (vue ADMIN d'une semaine vierge). */
    public record WeekDto(String week, LocalDate monday, WeekStatus status, boolean persisted, Instant updatedAt,
                          List<DayMenuDto> days, String previousWeek, String nextWeek) {
    }

    public record WeekSummaryDto(String week, LocalDate monday, WeekStatus status, int filledDays) {
    }
}
