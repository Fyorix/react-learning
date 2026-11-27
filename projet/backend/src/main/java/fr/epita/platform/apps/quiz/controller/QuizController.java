package fr.epita.platform.apps.quiz.controller;

import fr.epita.platform.apps.quiz.dto.QuizDtos.AttemptDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.AttemptRequest;
import fr.epita.platform.apps.quiz.dto.QuizDtos.AttemptResultDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.LeaderboardEntry;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuizDto;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuizRequest;
import fr.epita.platform.apps.quiz.dto.QuizDtos.QuizSummaryDto;
import fr.epita.platform.apps.quiz.service.QuizService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Quiz", description = "Quiz à choix multiples créés par les ADMIN, une tentative par utilisateur, classements.")
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService service;

    @Operation(summary = "Lister les quiz", description = "Accès : authentifié. USER : uniquement les quiz publiés, avec l'indicateur `attempted` ; ADMIN : tous.")
    @ApiResponse(responseCode = "200", description = "Quiz")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/quizzes")
    public List<QuizSummaryDto> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me);
    }

    @Operation(summary = "Détail d'un quiz", description = "Accès : authentifié. Les bonnes réponses (`correct`) ne sont jamais renvoyées à un USER ; un USER ne voit que les quiz publiés.")
    @ApiResponse(responseCode = "200", description = "Quiz")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Quiz introuvable (NOT_FOUND)")
    @GetMapping("/quizzes/{id}")
    public QuizDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @Operation(summary = "Créer un quiz", description = "Accès : ADMIN. Au moins 1 question ; chaque question a au moins 2 réponses dont au moins 1 bonne. Le quiz est créé non publié.")
    @ApiResponse(responseCode = "201", description = "Quiz créé")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @PostMapping("/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public QuizDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody QuizRequest req) {
        return service.create(me, req);
    }

    @Operation(summary = "Modifier un quiz", description = "Accès : ADMIN. Uniquement tant que le quiz n'est pas publié. Mêmes règles qu'à la création.")
    @ApiResponse(responseCode = "200", description = "Quiz modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Quiz introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Quiz publié (QUIZ_PUBLISHED)")
    @PutMapping("/quizzes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public QuizDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                          @Valid @RequestBody QuizRequest req) {
        return service.update(me, id, req);
    }

    @Operation(summary = "Supprimer un quiz", description = "Accès : ADMIN. Uniquement si aucune tentative.")
    @ApiResponse(responseCode = "204", description = "Quiz supprimé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Quiz introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Le quiz a des tentatives (QUIZ_HAS_ATTEMPTS)")
    @DeleteMapping("/quizzes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @Operation(summary = "Publier un quiz", description = "Accès : ADMIN. Le quiz devient visible des USER et n'est plus modifiable.")
    @ApiResponse(responseCode = "200", description = "Quiz publié")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Quiz introuvable (NOT_FOUND)")
    @PostMapping("/quizzes/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public QuizDto publish(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.publish(me, id);
    }

    @Operation(summary = "Dépublier un quiz", description = "Accès : ADMIN. Uniquement si aucune tentative.")
    @ApiResponse(responseCode = "200", description = "Quiz dépublié")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Quiz introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Le quiz a des tentatives (QUIZ_HAS_ATTEMPTS)")
    @PostMapping("/quizzes/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    public QuizDto unpublish(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.unpublish(me, id);
    }

    @Operation(summary = "Soumettre une tentative", description = "Accès : authentifié. Une seule tentative par utilisateur et par quiz publié. 1 point par question entièrement juste (toutes les bonnes réponses cochées, aucune mauvaise). Renvoie le score et la correction.")
    @ApiResponse(responseCode = "201", description = "Score et correction")
    @ApiResponse(responseCode = "400", description = "Données invalides (VALIDATION_ERROR) ou réponses ne correspondant pas au quiz (INVALID_ATTEMPT)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Quiz introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà tenté (ALREADY_ATTEMPTED)")
    @PostMapping("/quizzes/{id}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public AttemptResultDto submit(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                   @Valid @RequestBody AttemptRequest req) {
        return service.submit(me, id, req.answers());
    }

    @Operation(summary = "Mes tentatives", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Tentatives")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/me/attempts")
    public List<AttemptDto> myAttempts(@AuthenticationPrincipal AuthUser me) {
        return service.myAttempts(me);
    }

    @Operation(summary = "Classement d'un quiz", description = "Accès : authentifié. Score décroissant, puis date de tentative croissante en cas d'égalité.")
    @ApiResponse(responseCode = "200", description = "Classement")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Quiz introuvable (NOT_FOUND)")
    @GetMapping("/quizzes/{id}/leaderboard")
    public List<LeaderboardEntry> leaderboard(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.leaderboard(me, id);
    }

    @Operation(summary = "Classement général", description = "Accès : authentifié. Somme des scores de tous les quiz.")
    @ApiResponse(responseCode = "200", description = "Classement")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> globalLeaderboard() {
        return service.globalLeaderboard();
    }
}
