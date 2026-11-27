package fr.epita.platform.apps.defense.controller;

import fr.epita.platform.apps.defense.dto.DefenseDtos.*;
import fr.epita.platform.apps.defense.service.DefenseService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.dto.UserDtos.UserSummary;
import fr.epita.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Soutenances", description = "Planning des soutenances : créneaux, salles, jurys, choix des groupes et notes.")
@RestController
@RequestMapping("/api/defenses")
@RequiredArgsConstructor
public class DefenseController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String S404 = "Créneau introuvable (NOT_FOUND)";
    private static final String CONFLICTS = "ROOM_CONFLICT, TEACHER_CONFLICT ou SUPERVISOR_IN_JURY";

    private final DefenseService service;
    private final UserRepository users;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. TEACHER le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/slots")
    @Operation(summary = "Créneaux", description = "Accès : authentifié. Filtres : jour (`date`) et `free` (libres ou pris).")
    @ApiResponse(responseCode = "200", description = "Créneaux")
    @ApiResponse(responseCode = "400", description = "Date invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<SlotDto> slots(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) Boolean free) {
        return service.slots(date, free);
    }

    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un créneau", description = "Accès : ADMIN. Jury de 2 ou 3 enseignants ; salle et enseignants libres sur l'intervalle.")
    @ApiResponse(responseCode = "201", description = "Créneau")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Enseignant introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = CONFLICTS)
    public SlotDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SlotRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/slots/{id}")
    @Operation(summary = "Modifier un créneau", description = "Accès : ADMIN. Mêmes contrôles, et l'encadrant du groupe déjà placé ne peut pas entrer dans le jury.")
    @ApiResponse(responseCode = "200", description = "Créneau")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = S404)
    @ApiResponse(responseCode = "409", description = CONFLICTS)
    public SlotDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody SlotRequest req) {
        return service.update(me, id, req);
    }

    @DeleteMapping("/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un créneau", description = "Accès : ADMIN. Seulement s'il n'a pas été choisi.")
    @ApiResponse(responseCode = "204", description = "Supprimé")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = S404)
    @ApiResponse(responseCode = "409", description = "Choisi par un groupe (SLOT_TAKEN)")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @GetMapping("/groups")
    @Operation(summary = "Groupes", description = "Accès : ADMIN / TEACHER (tous) ; étudiant (le sien).")
    @ApiResponse(responseCode = "200", description = "Groupes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<GroupDto> groups(@AuthenticationPrincipal AuthUser me) {
        return service.groups(me);
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un groupe", description = "Accès : ADMIN. Encadrant TEACHER ; un étudiant n'appartient qu'à un groupe.")
    @ApiResponse(responseCode = "201", description = "Groupe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (GROUP_EXISTS)")
    public GroupDto createGroup(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody GroupRequest req) {
        return service.createGroup(me, req);
    }

    @PutMapping("/groups/{id}/slot")
    @Operation(summary = "Choisir un créneau", description = "Accès : étudiant du groupe. Créneau libre (choix simultanés : un seul réussit), encadrant hors jury ; changement possible jusqu'à 48 h avant.")
    @ApiResponse(responseCode = "200", description = "Groupe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas étudiant du groupe (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Groupe ou créneau introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SLOT_TAKEN, TOO_LATE ou SUPERVISOR_IN_JURY")
    public GroupDto choose(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ChooseRequest req) {
        return service.choose(me, id, req.slotId());
    }

    @GetMapping("/me/jury")
    @Operation(summary = "Mes jurys", description = "Accès : TEACHER. Créneaux où je siège, avec ma note éventuelle.")
    @ApiResponse(responseCode = "200", description = "Soutenances")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux TEACHER (FORBIDDEN)")
    public List<JuryDutyDto> myJury(@AuthenticationPrincipal AuthUser me) {
        return service.myJury(me);
    }

    @PutMapping("/slots/{id}/grades/me")
    @Operation(summary = "Noter", description = "Accès : membre du jury, après la soutenance. Note de 0 à 20 au quart de point, commentaire obligatoire ; modifiable.")
    @ApiResponse(responseCode = "200", description = "Ma note")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas membre du jury (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = S404)
    @ApiResponse(responseCode = "409", description = "DEFENSE_NOT_DONE ou NO_GROUP")
    public JuryDutyDto grade(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody GradeRequest req) {
        return service.grade(me, id, req);
    }

    @GetMapping("/groups/{id}/result")
    @Operation(summary = "Résultat", description = "Accès : étudiants du groupe, encadrant, jury, ADMIN. Moyenne publiée quand tout le jury a noté.")
    @ApiResponse(responseCode = "200", description = "Résultat")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Groupe introuvable ou non accessible (NOT_FOUND)")
    public ResultDto result(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.result(me, id);
    }

    @GetMapping("/planning")
    @Operation(summary = "Vue d'ensemble", description = "Accès : authentifié. Créneaux regroupés par salle (`by=room`, défaut) ou par enseignant (`by=teacher`).")
    @ApiResponse(responseCode = "200", description = "Planning")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public List<PlanningRow> planning(@RequestParam(required = false) String by) {
        return service.planning(by);
    }

    @GetMapping("/people")
    @Operation(summary = "Utilisateurs", description = "Accès : authentifié. Utilisateurs actifs (enseignants et étudiants).")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
