package fr.epita.platform.apps.timetable.controller;

import fr.epita.platform.apps.timetable.dto.TimetableDtos.*;
import fr.epita.platform.apps.timetable.service.TimetableService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.dto.UserDtos.UserSummary;
import fr.epita.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Emploi du temps", description = "Planning des groupes : salles, enseignants, matières, placement des cours sans conflit.")
@RestController
@RequestMapping("/api/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String CONFLICTS = "ROOM_CONFLICT, TEACHER_CONFLICT, GROUP_CONFLICT, ROOM_TOO_SMALL, HOURS_EXCEEDED ou COURSE_IN_PAST";
    private static final String REF404 = "Matière, groupe, salle ou enseignant introuvable (NOT_FOUND)";

    private final TimetableService service;
    private final UserRepository users;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. TEACHER le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/week")
    @Operation(summary = "Semaine d'un groupe, d'un enseignant ou d'une salle", description = "Accès : authentifié. Exactement un filtre ; `week` au format AAAA-Www (défaut : semaine courante).")
    @ApiResponse(responseCode = "200", description = "Cours")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public List<CourseDto> week(@RequestParam(required = false) String week, @RequestParam(required = false) UUID groupId,
                                @RequestParam(required = false) UUID teacherId, @RequestParam(required = false) UUID roomId) {
        return service.week(week, groupId, teacherId, roomId);
    }

    @GetMapping("/me")
    @Operation(summary = "Mon emploi du temps", description = "Accès : authentifié. Enseignant : ses cours ; étudiant : ceux de son groupe.")
    @ApiResponse(responseCode = "200", description = "Cours")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public List<CourseDto> mine(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String week) {
        return service.mine(me, week);
    }

    @PostMapping("/courses/check")
    @Operation(summary = "Vérifier un cours candidat", description = "Accès : ADMIN. Renvoie tous les conflits sans rien enregistrer ; `excludeId` : cours en cours de déplacement.")
    @ApiResponse(responseCode = "200", description = "Conflits")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = REF404)
    public CheckDto check(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CourseRequest req, @RequestParam(required = false) UUID excludeId) {
        return service.check(me, req, excludeId);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Placer un cours", description = "Accès : ADMIN. De 8 h à 20 h en quarts d'heure, 30 min à 4 h ; aucun conflit.")
    @ApiResponse(responseCode = "201", description = "Cours")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = REF404)
    @ApiResponse(responseCode = "409", description = CONFLICTS)
    public CourseDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CourseRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/courses/{id}")
    @Operation(summary = "Déplacer / modifier un cours", description = "Accès : ADMIN. Cours non commencé ; mêmes contrôles.")
    @ApiResponse(responseCode = "200", description = "Cours")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Cours ou référentiel introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = CONFLICTS)
    public CourseDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody CourseRequest req) {
        return service.update(me, id, req);
    }

    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un cours", description = "Accès : ADMIN. Cours non commencé.")
    @ApiResponse(responseCode = "204", description = "Supprimé")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Cours introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Cours passé (COURSE_IN_PAST)")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @GetMapping("/hours")
    @Operation(summary = "Volumes horaires", description = "Accès : authentifié. Heures prévues, planifiées et réalisées par matière d'un groupe.")
    @ApiResponse(responseCode = "200", description = "Volumes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Groupe introuvable (NOT_FOUND)")
    public List<HoursDto> hours(@RequestParam UUID groupId) {
        return service.hours(groupId);
    }

    @GetMapping("/rooms")
    @Operation(summary = "Salles", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Salles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RoomDto> rooms() {
        return service.rooms();
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une salle", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "201", description = "Salle")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (ROOM_EXISTS)")
    public RoomDto createRoom(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody RoomRequest req) {
        return service.createRoom(me, req);
    }

    @GetMapping("/groups")
    @Operation(summary = "Groupes", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Groupes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<GroupDto> groups() {
        return service.groups();
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un groupe", description = "Accès : ADMIN. Un étudiant n'appartient qu'à un groupe ; effectif ≥ inscrits.")
    @ApiResponse(responseCode = "201", description = "Groupe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Étudiant introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (GROUP_EXISTS)")
    public GroupDto createGroup(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody GroupRequest req) {
        return service.createGroup(me, req);
    }

    @GetMapping("/subjects")
    @Operation(summary = "Matières", description = "Accès : authentifié. Filtre facultatif par groupe.")
    @ApiResponse(responseCode = "200", description = "Matières")
    @ApiResponse(responseCode = "401", description = U401)
    public List<SubjectDto> subjects(@RequestParam(required = false) UUID groupId) {
        return service.subjects(groupId);
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une matière", description = "Accès : ADMIN. Nom unique dans le groupe.")
    @ApiResponse(responseCode = "201", description = "Matière")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Groupe introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Matière existante (SUBJECT_EXISTS)")
    public SubjectDto createSubject(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SubjectRequest req) {
        return service.createSubject(me, req);
    }

    @GetMapping("/people")
    @Operation(summary = "Utilisateurs", description = "Accès : authentifié. Utilisateurs actifs (enseignants et étudiants).")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
