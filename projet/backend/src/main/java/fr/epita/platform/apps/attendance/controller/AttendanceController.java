package fr.epita.platform.apps.attendance.controller;

import fr.epita.platform.apps.attendance.dto.AttendanceDtos.*;
import fr.epita.platform.apps.attendance.entity.JustificationStatus;
import fr.epita.platform.apps.attendance.service.AttendanceService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.dto.UserDtos.UserSummary;
import fr.epita.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Absences", description = "Appel des séances, justificatifs d'absence, compteurs et alertes.")
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String S404 = "Séance introuvable (NOT_FOUND)";
    private static final String J404 = "Justificatif introuvable (NOT_FOUND)";

    private final AttendanceService service;
    private final UserRepository users;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. TEACHER le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/groups")
    @Operation(summary = "Groupes", description = "Accès : TEACHER / ADMIN.")
    @ApiResponse(responseCode = "200", description = "Groupes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux enseignants et ADMIN (FORBIDDEN)")
    public List<GroupDto> groups(@AuthenticationPrincipal AuthUser me) {
        return service.groups(me);
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un groupe", description = "Accès : ADMIN. Un étudiant n'appartient qu'à un groupe.")
    @ApiResponse(responseCode = "201", description = "Groupe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Étudiant introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (GROUP_EXISTS)")
    public GroupDto createGroup(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody GroupRequest req) {
        return service.createGroup(me, req);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer des séances", description = "Accès : ADMIN. 8 h maximum ; `repeatWeeks` : même séance sur plusieurs semaines.")
    @ApiResponse(responseCode = "201", description = "Séances créées")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Groupe introuvable (NOT_FOUND)")
    public List<SessionDto> createSessions(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SessionRequest req) {
        return service.createSessions(me, req);
    }

    @GetMapping("/teacher/sessions")
    @Operation(summary = "Mes séances", description = "Accès : TEACHER. Filtre facultatif par jour.")
    @ApiResponse(responseCode = "200", description = "Séances")
    @ApiResponse(responseCode = "400", description = "Date invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux TEACHER (FORBIDDEN)")
    public List<SessionDto> teacherSessions(@AuthenticationPrincipal AuthUser me,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.teacherSessions(me, date);
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Séance et appel", description = "Accès : enseignant de la séance, ADMIN.")
    @ApiResponse(responseCode = "200", description = "Séance")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas l'enseignant de la séance (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = S404)
    public SessionDetailDto session(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.session(me, id);
    }

    @PutMapping("/sessions/{id}/roll-call")
    @Operation(summary = "Faire l'appel", description = "Accès : enseignant de la séance. À partir du début, modifiable jusqu'à 24 h après la fin.")
    @ApiResponse(responseCode = "200", description = "Séance et appel")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas l'enseignant de la séance (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = S404)
    @ApiResponse(responseCode = "409", description = "ATTENDANCE_LOCKED ou SESSION_NOT_STARTED")
    public SessionDetailDto rollCall(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RollCallRequest req) {
        return service.rollCall(me, id, req.entries());
    }

    @GetMapping("/me")
    @Operation(summary = "Mes absences et retards", description = "Accès : authentifié. Compteurs par matière (3 retards = 1 absence non justifiée).")
    @ApiResponse(responseCode = "200", description = "Absences et compteurs")
    @ApiResponse(responseCode = "401", description = U401)
    public MyAttendanceDto mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @PostMapping(value = "/justifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Déposer un justificatif", description = "Accès : étudiant. Multipart : `data` (JSON) et `file` (PDF, JPEG ou PNG, 5 Mo maximum) ; au plus tard 7 jours après chaque absence.")
    @ApiResponse(responseCode = "201", description = "Justificatif PENDING")
    @ApiResponse(responseCode = "400", description = "Données invalides (VALIDATION_ERROR) ou fichier refusé (INVALID_FILE)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Absence introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "JUSTIFICATION_TOO_LATE ou ALREADY_JUSTIFIED")
    public JustificationDto justify(@AuthenticationPrincipal AuthUser me, @Valid @RequestPart("data") JustificationRequest data,
                                    @RequestPart("file") MultipartFile file) {
        return service.justify(me, data, file);
    }

    @GetMapping("/justifications")
    @Operation(summary = "Justificatifs", description = "Accès : ADMIN (tous, filtre `status`) ; étudiant (les siens).")
    @ApiResponse(responseCode = "200", description = "Justificatifs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<JustificationDto> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) JustificationStatus status) {
        return service.list(me, status);
    }

    @GetMapping("/justifications/{id}/file")
    @Operation(summary = "Fichier du justificatif", description = "Accès : étudiant concerné, ADMIN.")
    @ApiResponse(responseCode = "200", description = "Fichier")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = J404)
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        AttendanceService.JustificationFile f = service.file(me, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.fileName() == null ? "justificatif" : f.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(f.content());
    }

    @PostMapping("/justifications/{id}/accept")
    @Operation(summary = "Accepter", description = "Accès : ADMIN. Les absences couvertes deviennent justifiées.")
    @ApiResponse(responseCode = "200", description = "Justificatif ACCEPTED")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = J404)
    @ApiResponse(responseCode = "409", description = "Déjà traité (INVALID_STATUS_TRANSITION)")
    public JustificationDto accept(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.decide(me, id, true, null);
    }

    @PostMapping("/justifications/{id}/reject")
    @Operation(summary = "Refuser", description = "Accès : ADMIN. Motif obligatoire.")
    @ApiResponse(responseCode = "200", description = "Justificatif REJECTED")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = J404)
    @ApiResponse(responseCode = "409", description = "Déjà traité (INVALID_STATUS_TRANSITION)")
    public JustificationDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return service.decide(me, id, false, req.reason());
    }

    @GetMapping("/admin/alerts")
    @Operation(summary = "Alertes", description = "Accès : ADMIN. Étudiants au-delà de 3 absences non justifiées dans une matière (retards compris, 3 pour 1).")
    @ApiResponse(responseCode = "200", description = "Alertes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<AlertDto> alerts(@AuthenticationPrincipal AuthUser me) {
        return service.alerts(me);
    }

    @GetMapping("/people")
    @Operation(summary = "Utilisateurs", description = "Accès : authentifié. Utilisateurs actifs (enseignants et étudiants).")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
