package fr.epita.platform.apps.grade.controller;

import fr.epita.platform.apps.grade.dto.GradeDtos.*;
import fr.epita.platform.apps.grade.service.GradeService;
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

@Tag(name = "Notes & bulletins", description = "Saisie des notes, moyennes pondérées, bulletins et verrouillage des périodes.")
@RestController
@RequestMapping("/api/grades")
@RequiredArgsConstructor
public class GradeController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String OWN = "Matière d'un autre enseignant (FORBIDDEN)";
    private static final String LOCKED = "Période verrouillée (PERIOD_LOCKED)";

    private final GradeService service;
    private final UserRepository users;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. TEACHER le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/classes")
    @Operation(summary = "Classes", description = "Accès : TEACHER / ADMIN.")
    @ApiResponse(responseCode = "200", description = "Classes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux enseignants et ADMIN (FORBIDDEN)")
    public List<ClassDto> classes(@AuthenticationPrincipal AuthUser me) {
        return service.classes(me);
    }

    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une classe", description = "Accès : ADMIN. Un étudiant n'appartient qu'à une classe.")
    @ApiResponse(responseCode = "201", description = "Classe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Étudiant introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (CLASS_EXISTS)")
    public ClassDto createClass(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ClassRequest req) {
        return service.createClass(me, req);
    }

    @GetMapping("/subjects")
    @Operation(summary = "Toutes les matières", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "200", description = "Matières")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<SubjectDto> allSubjects(@AuthenticationPrincipal AuthUser me) {
        return service.allSubjects(me);
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une matière", description = "Accès : ADMIN. Enseignant TEACHER ; nom unique dans la classe.")
    @ApiResponse(responseCode = "201", description = "Matière")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Classe introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Matière existante (SUBJECT_EXISTS)")
    public SubjectDto createSubject(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SubjectRequest req) {
        return service.createSubject(me, req);
    }

    @GetMapping("/periods")
    @Operation(summary = "Périodes", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Périodes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<PeriodDto> periods() {
        return service.periods();
    }

    @PostMapping("/periods")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une période", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "201", description = "Période")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (PERIOD_EXISTS)")
    public PeriodDto createPeriod(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PeriodRequest req) {
        return service.createPeriod(me, req);
    }

    @PostMapping("/periods/{id}/lock")
    @Operation(summary = "Verrouiller une période", description = "Accès : ADMIN. Plus aucune saisie ; bulletins visibles des étudiants.")
    @ApiResponse(responseCode = "200", description = "Période")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Période introuvable (NOT_FOUND)")
    public PeriodDto lock(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.setLocked(me, id, true);
    }

    @PostMapping("/periods/{id}/unlock")
    @Operation(summary = "Déverrouiller une période", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "200", description = "Période")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Période introuvable (NOT_FOUND)")
    public PeriodDto unlock(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.setLocked(me, id, false);
    }

    @GetMapping("/teacher/subjects")
    @Operation(summary = "Mes matières", description = "Accès : TEACHER. Matières enseignées et classes.")
    @ApiResponse(responseCode = "200", description = "Matières")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux TEACHER (FORBIDDEN)")
    public List<SubjectDto> teacherSubjects(@AuthenticationPrincipal AuthUser me) {
        return service.teacherSubjects(me);
    }

    @GetMapping("/subjects/{id}/assessments")
    @Operation(summary = "Évaluations d'une matière", description = "Accès : enseignant de la matière, ADMIN.")
    @ApiResponse(responseCode = "200", description = "Évaluations")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = "Matière introuvable (NOT_FOUND)")
    public List<AssessmentDto> subjectAssessments(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.subjectAssessments(me, id);
    }

    @PostMapping("/subjects/{id}/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une évaluation", description = "Accès : enseignant de la matière. Poids de 0,5 à 3 ; date dans une période non verrouillée.")
    @ApiResponse(responseCode = "201", description = "Évaluation")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = "Matière ou période introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = LOCKED)
    public AssessmentDto createAssessment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody AssessmentRequest req) {
        return service.createAssessment(me, id, req);
    }

    @GetMapping("/assessments/{id}")
    @Operation(summary = "Évaluation et notes", description = "Accès : enseignant de la matière, ADMIN.")
    @ApiResponse(responseCode = "200", description = "Évaluation")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = "Évaluation introuvable (NOT_FOUND)")
    public AssessmentDto assessment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.assessment(me, id);
    }

    @PutMapping("/assessments/{id}/marks")
    @Operation(summary = "Saisir les notes", description = "Accès : enseignant de la matière. 0 à 20 au quart de point, ou ABSENT (0) / ABSENT_EXCUSED (exclue).")
    @ApiResponse(responseCode = "200", description = "Évaluation")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = "Évaluation introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = LOCKED)
    public AssessmentDto saveMarks(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody MarksRequest req) {
        return service.saveMarks(me, id, req.marks());
    }

    @PutMapping("/subjects/{id}/periods/{periodId}/comments")
    @Operation(summary = "Appréciations d'une matière", description = "Accès : enseignant de la matière. Texte vide : appréciation supprimée.")
    @ApiResponse(responseCode = "200", description = "Appréciations enregistrées")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = "Matière ou période introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = LOCKED)
    public List<CommentInput> saveComments(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @PathVariable UUID periodId,
                                           @Valid @RequestBody CommentsRequest req) {
        return service.saveComments(me, id, periodId, req.comments());
    }

    @GetMapping("/me")
    @Operation(summary = "Mes notes et moyennes", description = "Accès : étudiant inscrit dans une classe.")
    @ApiResponse(responseCode = "200", description = "Notes et moyennes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Pas de classe ou période introuvable (NOT_FOUND)")
    public MyGradesDto mine(@AuthenticationPrincipal AuthUser me, @RequestParam UUID periodId) {
        return service.mine(me, periodId);
    }

    @GetMapping("/report-cards/{studentId}/{periodId}")
    @Operation(summary = "Bulletin", description = "Accès : enseignants de la classe, ADMIN, et l'étudiant concerné une fois la période verrouillée.")
    @ApiResponse(responseCode = "200", description = "Bulletin")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Bulletin introuvable ou non accessible (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Période non verrouillée (PERIOD_NOT_LOCKED)")
    public ReportCardDto reportCard(@AuthenticationPrincipal AuthUser me, @PathVariable UUID studentId, @PathVariable UUID periodId) {
        return service.reportCard(me, studentId, periodId);
    }

    @PutMapping("/report-cards/{studentId}/{periodId}/comment")
    @Operation(summary = "Appréciation générale", description = "Accès : ADMIN, période non verrouillée.")
    @ApiResponse(responseCode = "200", description = "Bulletin")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Étudiant ou période introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = LOCKED)
    public ReportCardDto saveGeneralComment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID studentId, @PathVariable UUID periodId,
                                            @Valid @RequestBody GeneralCommentRequest req) {
        return service.saveGeneralComment(me, studentId, periodId, req.text());
    }

    @GetMapping("/classes/{id}/summary")
    @Operation(summary = "Synthèse de la classe", description = "Accès : enseignants de la classe, ADMIN. Moyennes par matière, générale et rang.")
    @ApiResponse(responseCode = "200", description = "Synthèse")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux enseignants de la classe et ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Classe ou période introuvable (NOT_FOUND)")
    public ClassSummaryDto classSummary(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @RequestParam UUID periodId) {
        return service.classSummary(me, id, periodId);
    }

    @GetMapping("/people")
    @Operation(summary = "Utilisateurs", description = "Accès : authentifié. Utilisateurs actifs (étudiants et enseignants).")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
