package fr.epita.platform.apps.skills.controller;

import fr.epita.platform.apps.skills.dto.SkillsDtos.AskRequest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.CatalogRequest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.PairDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.ProfileDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.ProfileRequest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.RequestDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.SessionRequest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.SkillDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.StatsDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.SuggestionDto;
import fr.epita.platform.apps.skills.service.SkillsService;
import fr.epita.platform.common.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@Tag(name = "Bourse aux compétences", description = "Mise en relation entre mentors et apprenants selon les compétences, suivi des séances.")
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillsController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";

    private final SkillsService service;

    @GetMapping("/catalog")
    @Operation(summary = "Référentiel de compétences", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Compétences")
    @ApiResponse(responseCode = "401", description = U401)
    public List<SkillDto> catalog() {
        return service.catalog();
    }

    @PostMapping("/catalog")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une compétence", description = "Accès : ADMIN. Nom unique (ajout au §5.22).")
    @ApiResponse(responseCode = "201", description = "Compétence ajoutée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "Compétence existante (SKILL_EXISTS)")
    public SkillDto addSkill(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CatalogRequest req) {
        return service.addSkill(me, req.name());
    }

    @GetMapping("/profile/me")
    @Operation(summary = "Mon profil", description = "Accès : authentifié. Compétences enseignées, compétences recherchées, capacité et nombre d'apprenants actifs.")
    @ApiResponse(responseCode = "200", description = "Profil")
    @ApiResponse(responseCode = "401", description = U401)
    public ProfileDto profile(@AuthenticationPrincipal AuthUser me) {
        return service.profile(me);
    }

    @PutMapping("/profile/me")
    @Operation(summary = "Modifier mon profil", description = "Accès : authentifié. Niveau ≥ 3 pour enseigner ; capacité 1 à 5, jamais sous le nombre d'apprenants actifs.")
    @ApiResponse(responseCode = "200", description = "Profil")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public ProfileDto updateProfile(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ProfileRequest req) {
        return service.updateProfile(me, req);
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Mentors suggérés", description = "Accès : authentifié. Mentors de niveau ≥ 3 sur `skillId` ayant encore de la place, triés par niveau puis par places restantes ; jamais soi-même.")
    @ApiResponse(responseCode = "200", description = "Suggestions")
    @ApiResponse(responseCode = "400", description = "Paramètre manquant (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<SuggestionDto> suggestions(@AuthenticationPrincipal AuthUser me, @RequestParam UUID skillId) {
        return service.suggestions(me, skillId);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demander un mentor", description = "Accès : authentifié. Pas soi-même ; une seule demande en attente ou binôme actif par compétence. Une demande sans réponse expire au bout de 14 jours.")
    @ApiResponse(responseCode = "201", description = "Demande envoyée (PENDING)")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Compétence ou mentor introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SELF_MENTORING ou ALREADY_PAIRED")
    public RequestDto ask(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AskRequest req) {
        return service.ask(me, req);
    }

    @GetMapping("/requests")
    @Operation(summary = "Mes demandes", description = "Accès : authentifié. `box` = sent (envoyées, par défaut) ou received (reçues comme mentor).")
    @ApiResponse(responseCode = "200", description = "Demandes")
    @ApiResponse(responseCode = "400", description = "Boîte inconnue (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RequestDto> requests(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String box) {
        return service.myRequests(me, box);
    }

    @PostMapping("/requests/{id}/accept")
    @Operation(summary = "Accepter une demande", description = "Accès : mentor sollicité. Crée le binôme ; refusé au-delà de la capacité.")
    @ApiResponse(responseCode = "200", description = "Binôme créé")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le mentor sollicité (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Demande introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "MENTOR_FULL ou INVALID_STATUS_TRANSITION (demande expirée ou déjà traitée)")
    public PairDto accept(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.accept(me, id);
    }

    @PostMapping("/requests/{id}/decline")
    @Operation(summary = "Refuser une demande", description = "Accès : mentor sollicité.")
    @ApiResponse(responseCode = "200", description = "Demande refusée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le mentor sollicité (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Demande introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Demande expirée ou déjà traitée (INVALID_STATUS_TRANSITION)")
    public RequestDto decline(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.decline(me, id);
    }

    @GetMapping("/pairs/me")
    @Operation(summary = "Mes binômes", description = "Accès : authentifié. Comme mentor et comme apprenant, avec le journal des séances.")
    @ApiResponse(responseCode = "200", description = "Binômes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<PairDto> pairs(@AuthenticationPrincipal AuthUser me) {
        return service.myPairs(me);
    }

    @PostMapping("/pairs/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Saisir une séance", description = "Accès : membres du binôme actif.")
    @ApiResponse(responseCode = "201", description = "Binôme avec la séance")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas membre du binôme (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Binôme introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Binôme terminé (PAIR_ENDED)")
    public PairDto addSession(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody SessionRequest req) {
        return service.addSession(me, id, req);
    }

    @PostMapping("/pairs/{id}/end")
    @Operation(summary = "Terminer le binôme", description = "Accès : l'un ou l'autre des membres. Libère une place chez le mentor.")
    @ApiResponse(responseCode = "200", description = "Binôme terminé")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas membre du binôme (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Binôme introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà terminé (PAIR_ENDED)")
    public PairDto end(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.end(me, id);
    }

    @GetMapping("/admin/stats")
    @Operation(summary = "Statistiques", description = "Accès : ADMIN. Compétences les plus demandées, mentors les plus sollicités, binômes actifs.")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    public StatsDto stats(@AuthenticationPrincipal AuthUser me) {
        return service.stats(me);
    }
}
