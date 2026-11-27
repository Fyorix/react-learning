package fr.epita.platform.apps.birthday.controller;

import fr.epita.platform.apps.birthday.dto.BirthdayDtos.*;
import fr.epita.platform.apps.birthday.service.BirthdayService;
import fr.epita.platform.common.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Anniversaires", description = "Calendrier des anniversaires de naissance et d'arrivée, rappels et import des fiches.")
@RestController
@RequestMapping("/api/birthdays")
@RequiredArgsConstructor
public class BirthdayController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";

    private final BirthdayService service;

    @GetMapping("/calendar")
    @Operation(summary = "Événements du mois", description = "Accès : authentifié. Naissances (sans année, absentes si masquées) et anciennetés (`years`, jalons) du mois `AAAA-MM` ; filtre par équipe.")
    @ApiResponse(responseCode = "200", description = "Événements")
    @ApiResponse(responseCode = "400", description = "Mois invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<EventDto> calendar(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                   @RequestParam(required = false) UUID teamId) {
        return service.calendar(month, teamId);
    }

    @GetMapping("/upcoming")
    @Operation(summary = "À venir", description = "Accès : authentifié. Événements des `days` prochains jours (30 par défaut, 366 maximum).")
    @ApiResponse(responseCode = "200", description = "Événements")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public List<EventDto> upcoming(@RequestParam(required = false) Integer days, @RequestParam(required = false) UUID teamId) {
        return service.upcoming(days, teamId);
    }

    @GetMapping("/reminders")
    @Operation(summary = "Rappels", description = "Accès : authentifié. Événements du jour et du lendemain, dans le fuseau Europe/Paris.")
    @ApiResponse(responseCode = "200", description = "Rappels")
    @ApiResponse(responseCode = "401", description = U401)
    public RemindersDto reminders() {
        return service.reminders();
    }

    @GetMapping("/me")
    @Operation(summary = "Ma fiche", description = "Accès : authentifié. Seul endroit où l'année de naissance est renvoyée.")
    @ApiResponse(responseCode = "200", description = "Fiche")
    @ApiResponse(responseCode = "401", description = U401)
    public MeDto me(@AuthenticationPrincipal AuthUser me) {
        return service.me(me);
    }

    @PutMapping("/me/preferences")
    @Operation(summary = "Mes préférences", description = "Accès : authentifié, fiche existante. Masquer mon anniversaire de naissance (l'ancienneté reste visible).")
    @ApiResponse(responseCode = "200", description = "Fiche")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Pas encore de fiche (NOT_FOUND)")
    public MeDto preferences(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PreferencesRequest req) {
        return service.preferences(me, req.showBirthday());
    }

    @GetMapping("/admin/people")
    @Operation(summary = "Fiches", description = "Accès : ADMIN. Jour et mois de naissance uniquement.")
    @ApiResponse(responseCode = "200", description = "Fiches")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<PersonDto> people(@AuthenticationPrincipal AuthUser me) {
        return service.people(me);
    }

    @PutMapping("/admin/people/{userId}")
    @Operation(summary = "Saisir une fiche", description = "Accès : ADMIN. Naissance facultative et passée, arrivée pas dans le futur.")
    @ApiResponse(responseCode = "200", description = "Fiche")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Utilisateur ou équipe introuvable (NOT_FOUND)")
    public PersonDto save(@AuthenticationPrincipal AuthUser me, @PathVariable UUID userId, @Valid @RequestBody PersonRequest req) {
        return service.save(me, userId, req);
    }

    @PostMapping(value = "/admin/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importer un CSV", description = "Accès : ADMIN. Partie `file`, colonnes `email;birthDate;hireDate;team` (dates AAAA-MM-JJ, en-tête facultatif, équipe créée si besoin). Lignes invalides signalées, les autres importées.")
    @ApiResponse(responseCode = "200", description = "Rapport ligne par ligne")
    @ApiResponse(responseCode = "400", description = "Fichier manquant ou trop volumineux (INVALID_FILE)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public ImportReport importCsv(@AuthenticationPrincipal AuthUser me, @RequestPart("file") MultipartFile file) {
        return service.importCsv(me, file);
    }

    @GetMapping("/teams")
    @Operation(summary = "Équipes", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Équipes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<TeamDto> teams() {
        return service.teams();
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une équipe", description = "Accès : ADMIN. Nom unique.")
    @ApiResponse(responseCode = "201", description = "Équipe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (TEAM_EXISTS)")
    public TeamDto createTeam(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TeamRequest req) {
        return service.createTeam(me, req);
    }
}
