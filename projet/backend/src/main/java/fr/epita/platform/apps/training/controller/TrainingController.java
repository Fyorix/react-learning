package fr.epita.platform.apps.training.controller;

import fr.epita.platform.apps.training.dto.TrainingDtos.AttendanceRequest;
import fr.epita.platform.apps.training.dto.TrainingDtos.CertificateDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.CourseDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.CourseRequest;
import fr.epita.platform.apps.training.dto.TrainingDtos.EnrollmentDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.RejectRequest;
import fr.epita.platform.apps.training.dto.TrainingDtos.SessionDetailDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.SessionRequest;
import fr.epita.platform.apps.training.dto.TrainingDtos.StatsDto;
import fr.epita.platform.apps.training.entity.EnrollmentStatus;
import fr.epita.platform.apps.training.service.TrainingService;
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

@Tag(name = "Formations", description = "Catalogue de formations internes, inscriptions validées par le manager, émargement et attestations.")
@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class TrainingController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String MGR = "Pas le manager du salarié, ou sa propre inscription (FORBIDDEN)";

    private final TrainingService service;
    private final UserRepository users;

    @GetMapping("/courses")
    @Operation(summary = "Catalogue", description = "Accès : authentifié. Formations avec leurs sessions à venir ; filtres `theme` et `search`.")
    @ApiResponse(responseCode = "200", description = "Formations")
    @ApiResponse(responseCode = "401", description = U401)
    public List<CourseDto> catalog(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String theme,
                                   @RequestParam(required = false) String search) {
        return service.catalog(me, theme, search);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une formation", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "201", description = "Formation créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Formateur introuvable (NOT_FOUND)")
    public CourseDto createCourse(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CourseRequest req) {
        return service.createCourse(me, req);
    }

    @PostMapping("/courses/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une session", description = "Accès : ADMIN. Début dans le futur, fin après le début.")
    @ApiResponse(responseCode = "201", description = "Formation avec la nouvelle session")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Formation introuvable (NOT_FOUND)")
    public CourseDto addSession(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody SessionRequest req) {
        return service.addSession(me, id, req);
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Détail d'une session", description = "Accès : authentifié. Places restantes et mon statut ; la liste des inscrits n'est renvoyée qu'au formateur et aux ADMIN.")
    @ApiResponse(responseCode = "200", description = "Session")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Session introuvable (NOT_FOUND)")
    public SessionDetailDto session(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.session(me, id);
    }

    @PostMapping("/sessions/{id}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demander l'inscription", description = "Accès : authentifié. La demande (REQUESTED) attend l'accord du manager. Refusée pour une session commencée, un doublon ou un chevauchement avec une autre inscription.")
    @ApiResponse(responseCode = "201", description = "Demande enregistrée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Session introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SESSION_CLOSED, ALREADY_ENROLLED ou SESSION_OVERLAP")
    public EnrollmentDto enroll(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.enroll(me, id);
    }

    @DeleteMapping("/sessions/{id}/enrollments/me")
    @Operation(summary = "Me désinscrire", description = "Accès : inscrit. Jusqu'à 48 h avant la session ; la place libérée confirme le premier de la liste d'attente dans la même transaction.")
    @ApiResponse(responseCode = "200", description = "Inscription annulée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Session ou inscription introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Trop tard (TOO_LATE_TO_CANCEL)")
    public EnrollmentDto cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.cancel(me, id);
    }

    @GetMapping("/me/enrollments")
    @Operation(summary = "Mes formations", description = "Accès : authentifié. Toutes mes inscriptions, des plus récentes aux plus anciennes.")
    @ApiResponse(responseCode = "200", description = "Inscriptions")
    @ApiResponse(responseCode = "401", description = U401)
    public List<EnrollmentDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/team/enrollments")
    @Operation(summary = "Inscriptions de mes salariés", description = "Accès : manager (ADMIN : toutes). `status` : REQUESTED pour les demandes à valider.")
    @ApiResponse(responseCode = "200", description = "Inscriptions")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<EnrollmentDto> team(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) EnrollmentStatus status) {
        return service.team(me, status);
    }

    @PostMapping("/enrollments/{id}/approve")
    @Operation(summary = "Accepter une inscription", description = "Accès : manager du salarié ou ADMIN. CONFIRMED s'il reste une place, sinon WAITLISTED (dans l'ordre des accords).")
    @ApiResponse(responseCode = "200", description = "Inscription confirmée ou en liste d'attente")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = "Inscription introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà traitée (INVALID_STATUS_TRANSITION)")
    public EnrollmentDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.approve(me, id);
    }

    @PostMapping("/enrollments/{id}/reject")
    @Operation(summary = "Refuser une inscription", description = "Accès : manager du salarié ou ADMIN. Motif obligatoire.")
    @ApiResponse(responseCode = "200", description = "Inscription refusée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = "Inscription introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà traitée (INVALID_STATUS_TRANSITION)")
    public EnrollmentDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return service.reject(me, id, req.reason());
    }

    @PutMapping("/sessions/{id}/attendance")
    @Operation(summary = "Émargement", description = "Accès : formateur de la session ou ADMIN, une fois la session terminée. Les inscrits confirmés absents de la liste sont notés absents.")
    @ApiResponse(responseCode = "200", description = "Session avec la présence")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Ni formateur ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Session introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Session pas encore terminée (SESSION_NOT_DONE)")
    public SessionDetailDto attendance(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody AttendanceRequest req) {
        return service.attendance(me, id, req.presentUserIds());
    }

    @GetMapping("/enrollments/{id}/certificate")
    @Operation(summary = "Attestation", description = "Accès : le participant ou un ADMIN. Seulement si présent à la session.")
    @ApiResponse(responseCode = "200", description = "Attestation")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Inscription introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Absent (NOT_ATTENDED)")
    public CertificateDto certificate(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.certificate(me, id);
    }

    @GetMapping("/admin/stats")
    @Operation(summary = "Statistiques", description = "Accès : ADMIN. Remplissage des sessions de l'année et heures de formation suivies par salarié (présents).")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public StatsDto stats(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Integer year) {
        return service.stats(me, year);
    }

    @GetMapping("/candidates")
    @Operation(summary = "Formateurs possibles", description = "Accès : authentifié. Utilisateurs actifs.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> candidates() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
