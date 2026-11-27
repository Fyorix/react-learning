package fr.epita.platform.apps.canteen.controller;

import fr.epita.platform.apps.canteen.dto.CanteenDtos.DayMenuRequest;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.DishDto;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.DishRequest;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.GenerateRequest;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.WeekDto;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.WeekSummaryDto;
import fr.epita.platform.apps.canteen.entity.DishCategory;
import fr.epita.platform.apps.canteen.service.CanteenService;
import fr.epita.platform.common.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Cantine", description = "Catalogue de plats et planning des menus de la semaine, avec génération aléatoire.")
@RestController
@RequestMapping("/api/canteen")
@RequiredArgsConstructor
public class CanteenController {

    private final CanteenService service;

    @Operation(summary = "Catalogue des plats", description = "Accès : authentifié. Filtres `category` (STARTER, MAIN, DESSERT), `vegetarian`, `archived`.")
    @ApiResponse(responseCode = "200", description = "Plats")
    @ApiResponse(responseCode = "400", description = "Filtre invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/dishes")
    public List<DishDto> dishes(@RequestParam(required = false) DishCategory category,
                                @RequestParam(required = false) Boolean vegetarian,
                                @RequestParam(required = false) Boolean archived) {
        return service.dishes(category, vegetarian, archived);
    }

    @Operation(summary = "Ajouter un plat", description = "Accès : ADMIN. Nom unique dans sa catégorie.")
    @ApiResponse(responseCode = "201", description = "Plat ajouté")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "Nom déjà utilisé dans la catégorie (DISH_ALREADY_EXISTS)")
    @PostMapping("/dishes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DishDto createDish(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody DishRequest req) {
        return service.createDish(me, req);
    }

    @Operation(summary = "Modifier un plat", description = "Accès : ADMIN. Nom unique dans sa catégorie ; la catégorie ne change pas si le plat est utilisé dans un planning.")
    @ApiResponse(responseCode = "200", description = "Plat modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Plat introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "DISH_ALREADY_EXISTS ou DISH_IN_USE")
    @PutMapping("/dishes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DishDto updateDish(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                              @Valid @RequestBody DishRequest req) {
        return service.updateDish(me, id, req);
    }

    @Operation(summary = "Archiver / désarchiver un plat", description = "Accès : ADMIN. Bascule l'archivage ; un plat archivé sort du tirage aléatoire.")
    @ApiResponse(responseCode = "200", description = "Plat")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Plat introuvable (NOT_FOUND)")
    @PostMapping("/dishes/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public DishDto archive(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.toggleArchive(me, id);
    }

    @Operation(summary = "Supprimer un plat", description = "Accès : ADMIN. Impossible si le plat est utilisé dans un planning à venir (l'archiver à la place).")
    @ApiResponse(responseCode = "204", description = "Plat supprimé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Plat introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Plat utilisé (DISH_IN_USE)")
    @DeleteMapping("/dishes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteDish(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.deleteDish(me, id);
    }

    @Operation(summary = "Planning d'une semaine", description = "Accès : authentifié. `week` au format ISO AAAA-Www (ex. 2026-W47). USER : uniquement un planning publié ; ADMIN : aussi les brouillons et les semaines vierges.")
    @ApiResponse(responseCode = "200", description = "Planning")
    @ApiResponse(responseCode = "400", description = "Semaine invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Menu pas encore publié (MENU_NOT_PUBLISHED)")
    @GetMapping("/weeks/{week}")
    public WeekDto week(@AuthenticationPrincipal AuthUser me, @PathVariable String week) {
        return service.week(me, week);
    }

    @Operation(summary = "Composer le menu d'un jour", description = "Accès : ADMIN, planning en brouillon. `week` au format ISO AAAA-Www (ex. 2026-W47). `dayOfWeek` = MONDAY à FRIDAY. Exactement une entrée, un plat et un dessert, chacun de la bonne catégorie et non archivé.")
    @ApiResponse(responseCode = "200", description = "Planning")
    @ApiResponse(responseCode = "400", description = "Semaine, jour ou plat invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "Planning publié (MENU_PUBLISHED)")
    @PutMapping("/weeks/{week}/days/{dayOfWeek}")
    @PreAuthorize("hasRole('ADMIN')")
    public WeekDto setDay(@AuthenticationPrincipal AuthUser me, @PathVariable String week,
                          @PathVariable String dayOfWeek, @Valid @RequestBody DayMenuRequest req) {
        return service.setDay(me, week, dayOfWeek, req);
    }

    @Operation(summary = "Vider un jour", description = "Accès : ADMIN, planning en brouillon. `week` au format ISO AAAA-Www (ex. 2026-W47). `dayOfWeek` = MONDAY à FRIDAY.")
    @ApiResponse(responseCode = "200", description = "Planning")
    @ApiResponse(responseCode = "400", description = "Semaine ou jour invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "Planning publié (MENU_PUBLISHED)")
    @DeleteMapping("/weeks/{week}/days/{dayOfWeek}")
    @PreAuthorize("hasRole('ADMIN')")
    public WeekDto clearDay(@AuthenticationPrincipal AuthUser me, @PathVariable String week,
                            @PathVariable String dayOfWeek) {
        return service.clearDay(me, week, dayOfWeek);
    }

    @Operation(summary = "Génération aléatoire", description = "Accès : ADMIN, planning en brouillon. `week` au format ISO AAAA-Www (ex. 2026-W47). Remplit les jours vides (`overwrite=true` : toute la semaine). Pas deux fois le même plat dans la semaine tant que le catalogue le permet ; sinon jamais deux jours de suite. `vegetarianDays` : nombre minimum de jours avec un plat principal végétarien.")
    @ApiResponse(responseCode = "200", description = "Planning généré")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "CATALOG_INCOMPLETE, MENU_PUBLISHED, VEGETARIAN_DAYS_UNREACHABLE ou NOT_ENOUGH_VEGETARIAN_DISHES")
    @PostMapping("/weeks/{week}/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public WeekDto generate(@AuthenticationPrincipal AuthUser me, @PathVariable String week,
                            @Valid @RequestBody(required = false) GenerateRequest req) {
        return service.generate(me, week, req);
    }

    @Operation(summary = "Publier le planning", description = "Accès : ADMIN. Les 5 jours doivent être complets. Le planning devient visible des USER.")
    @ApiResponse(responseCode = "200", description = "Planning publié")
    @ApiResponse(responseCode = "400", description = "Semaine invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Planning introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Planning incomplet (MENU_INCOMPLETE)")
    @PostMapping("/weeks/{week}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public WeekDto publish(@AuthenticationPrincipal AuthUser me, @PathVariable String week) {
        return service.publish(me, week);
    }

    @Operation(summary = "Repasser en brouillon", description = "Accès : ADMIN. Le planning redevient modifiable et regénérable.")
    @ApiResponse(responseCode = "200", description = "Planning en brouillon")
    @ApiResponse(responseCode = "400", description = "Semaine invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Planning introuvable (NOT_FOUND)")
    @PostMapping("/weeks/{week}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    public WeekDto unpublish(@AuthenticationPrincipal AuthUser me, @PathVariable String week) {
        return service.unpublish(me, week);
    }

    @Operation(summary = "Liste des plannings", description = "Accès : ADMIN. Semaines `from` à `to` (format AAAA-Www) et leur statut.")
    @ApiResponse(responseCode = "200", description = "Plannings")
    @ApiResponse(responseCode = "400", description = "Semaine invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @GetMapping("/weeks")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WeekSummaryDto> list(@AuthenticationPrincipal AuthUser me,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to) {
        return service.list(me, from, to);
    }
}
