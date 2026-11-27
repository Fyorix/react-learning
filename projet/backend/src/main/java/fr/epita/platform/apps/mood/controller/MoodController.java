package fr.epita.platform.apps.mood.controller;

import fr.epita.platform.apps.mood.dto.MoodDtos.AdminMoodStatsDto;
import fr.epita.platform.apps.mood.dto.MoodDtos.MoodEntryDto;
import fr.epita.platform.apps.mood.dto.MoodDtos.MoodEntryRequest;
import fr.epita.platform.apps.mood.dto.MoodDtos.MoodStatsDto;
import fr.epita.platform.apps.mood.service.MoodService;
import fr.epita.platform.common.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Mood Tracker", description = "Suivi quotidien de l'humeur : une entrée par utilisateur et par jour.")
@RestController
@RequestMapping("/api/mood")
@RequiredArgsConstructor
public class MoodController {

    private final MoodService service;

    @Operation(summary = "Créer ou mettre à jour l'entrée d'un jour", description = "Accès : authentifié. Une seule entrée par utilisateur et par jour : une nouvelle saisie le même jour remplace l'entrée. `date` au format ISO (AAAA-MM-JJ), pas dans le futur.")
    @ApiResponse(responseCode = "200", description = "Entrée enregistrée")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @PutMapping("/entries/{date}")
    public MoodEntryDto upsert(@AuthenticationPrincipal AuthUser me,
                               @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @Valid @RequestBody MoodEntryRequest req) {
        return service.upsert(me, date, req);
    }

    @Operation(summary = "Historique de mes humeurs", description = "Accès : authentifié. Uniquement les entrées de l'utilisateur connecté, filtrées par `from` / `to` (dates ISO incluses). `from` ≤ `to`.")
    @ApiResponse(responseCode = "200", description = "Entrées triées par date")
    @ApiResponse(responseCode = "400", description = "Période invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/entries")
    public List<MoodEntryDto> history(@AuthenticationPrincipal AuthUser me,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.history(me, from, to);
    }

    @Operation(summary = "Entrée d'une date", description = "Accès : authentifié. Uniquement sa propre entrée.")
    @ApiResponse(responseCode = "200", description = "Entrée")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Entrée introuvable (NOT_FOUND)")
    @GetMapping("/entries/{date}")
    public MoodEntryDto get(@AuthenticationPrincipal AuthUser me,
                            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.get(me, date);
    }

    @Operation(summary = "Supprimer une entrée", description = "Accès : authentifié. Uniquement sa propre entrée.")
    @ApiResponse(responseCode = "204", description = "Entrée supprimée")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Entrée introuvable (NOT_FOUND)")
    @DeleteMapping("/entries/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me,
                       @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.delete(me, date);
    }

    @Operation(summary = "Mes statistiques", description = "Accès : authentifié. `period` = week, month ou year (période glissante se terminant aujourd'hui) : moyenne, répartition des scores 1 à 5, série de jours consécutifs.")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "400", description = "Période inconnue (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/stats")
    public MoodStatsDto stats(@AuthenticationPrincipal AuthUser me,
                              @RequestParam(defaultValue = "week") String period) {
        return service.stats(me, period);
    }

    @Operation(summary = "Statistiques globales anonymisées", description = "Accès : ADMIN. Aucun identifiant d'utilisateur n'est renvoyé.")
    @ApiResponse(responseCode = "200", description = "Statistiques globales")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminMoodStatsDto adminStats() {
        return service.adminStats();
    }
}
