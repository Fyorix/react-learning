package fr.epita.platform.apps.recruitment.controller;

import fr.epita.platform.apps.recruitment.dto.RecruitmentDtos.*;
import fr.epita.platform.apps.recruitment.entity.JobStatus;
import fr.epita.platform.apps.recruitment.service.RecruitmentService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.dto.UserDtos.UserSummary;
import fr.epita.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Tag(name = "Recrutement", description = "Postes, pipeline des candidatures, évaluations d'entretien et statistiques.")
@RestController
@RequestMapping("/api/recruitment")
@RequiredArgsConstructor
public class RecruitmentController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String REC = "Réservé aux RECRUITER (FORBIDDEN)";
    private static final String A404 = "Candidature introuvable ou non accessible (NOT_FOUND)";

    private final RecruitmentService service;
    private final UserRepository users;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. RECRUITER le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/jobs")
    @Operation(summary = "Postes", description = "Accès : authentifié. Filtre facultatif par statut.")
    @ApiResponse(responseCode = "200", description = "Postes")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<JobDto> jobs(@RequestParam(required = false) JobStatus status) {
        return service.jobs(status);
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un poste", description = "Accès : RECRUITER.")
    @ApiResponse(responseCode = "201", description = "Poste créé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = REC)
    public JobDto createJob(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody JobRequest req) {
        return service.createJob(me, req);
    }

    @PutMapping("/jobs/{id}")
    @Operation(summary = "Modifier un poste", description = "Accès : RECRUITER. Y compris le statut (OPEN, ON_HOLD, FILLED, CLOSED).")
    @ApiResponse(responseCode = "200", description = "Poste")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = REC)
    @ApiResponse(responseCode = "404", description = "Poste introuvable (NOT_FOUND)")
    public JobDto updateJob(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody JobRequest req) {
        return service.updateJob(me, id, req);
    }

    @PostMapping(value = "/jobs/{id}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une candidature", description = "Accès : RECRUITER. Multipart : `data` (candidat, JSON) et `cv` (PDF, 5 Mo maximum). Poste ouvert ; une candidature par e-mail et par poste.")
    @ApiResponse(responseCode = "201", description = "Candidature en SCREENING")
    @ApiResponse(responseCode = "400", description = "Données invalides (VALIDATION_ERROR) ou fichier refusé (INVALID_FILE)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = REC)
    @ApiResponse(responseCode = "404", description = "Poste introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "JOB_NOT_OPEN ou ALREADY_APPLIED")
    public ApplicationDto apply(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                @Valid @RequestPart("data") CandidateRequest data, @RequestPart("cv") MultipartFile cv) {
        return service.apply(me, id, data, cv);
    }

    @GetMapping("/jobs/{id}/pipeline")
    @Operation(summary = "Pipeline d'un poste", description = "Accès : RECRUITER. Candidatures groupées par étape (les plus anciennes dans l'étape d'abord).")
    @ApiResponse(responseCode = "200", description = "Pipeline")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = REC)
    @ApiResponse(responseCode = "404", description = "Poste introuvable (NOT_FOUND)")
    public PipelineDto pipeline(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.pipeline(me, id);
    }

    @GetMapping("/applications/{id}")
    @Operation(summary = "Fiche candidat", description = "Accès : RECRUITER ou intervenant désigné. Candidat, historique des étapes, évaluations.")
    @ApiResponse(responseCode = "200", description = "Candidature")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = A404)
    public ApplicationDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @GetMapping("/applications/{id}/cv")
    @Operation(summary = "CV", description = "Accès : RECRUITER ou intervenant désigné.")
    @ApiResponse(responseCode = "200", description = "PDF")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = A404)
    public ResponseEntity<byte[]> cv(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        RecruitmentService.Cv cv = service.cv(me, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(cv.fileName() == null ? "cv.pdf" : cv.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(cv.content());
    }

    @PostMapping("/applications/{id}/move")
    @Operation(summary = "Changer d'étape", description = "Accès : RECRUITER. Jamais en arrière ; motif obligatoire pour REJECTED ou pour sauter une étape ; OFFER exige 2 évaluations YES ; HIRED passe le poste en FILLED. `interviewerIds` (facultatif) désigne les intervenants.")
    @ApiResponse(responseCode = "200", description = "Candidature")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = REC)
    @ApiResponse(responseCode = "404", description = "Candidature ou intervenant introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "INVALID_STAGE_TRANSITION ou NOT_ENOUGH_APPROVALS")
    public ApplicationDto move(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody MoveRequest req) {
        return service.move(me, id, req);
    }

    @PostMapping("/applications/{id}/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Évaluer", description = "Accès : intervenant désigné (ou RECRUITER). Étape en cours uniquement ; une évaluation par intervenant et par étape.")
    @ApiResponse(responseCode = "201", description = "Candidature")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas intervenant (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = A404)
    @ApiResponse(responseCode = "409", description = "ALREADY_EVALUATED ou INVALID_STAGE_TRANSITION (étape non en cours)")
    public ApplicationDto evaluate(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody EvaluationRequest req) {
        return service.evaluate(me, id, req);
    }

    @GetMapping("/me/evaluations")
    @Operation(summary = "Mes évaluations à faire", description = "Accès : authentifié. Candidatures où je suis intervenant et dont l'étape en cours n'est pas encore évaluée par moi.")
    @ApiResponse(responseCode = "200", description = "À faire")
    @ApiResponse(responseCode = "401", description = U401)
    public List<TodoDto> todo(@AuthenticationPrincipal AuthUser me) {
        return service.myTodo(me);
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques", description = "Accès : RECRUITER. Délai moyen candidature → embauche, taux de passage par étape.")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = REC)
    public StatsDto stats(@AuthenticationPrincipal AuthUser me) {
        return service.stats(me);
    }

    @GetMapping("/people")
    @Operation(summary = "Intervenants possibles", description = "Accès : authentifié. Utilisateurs actifs.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
