package fr.epita.platform.apps.kudos.controller;

import fr.epita.platform.apps.kudos.dto.KudosDtos.KudoDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.KudoRequest;
import fr.epita.platform.apps.kudos.dto.KudosDtos.ProfileDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.RankDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.ValueDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.ValueRequest;
import fr.epita.platform.apps.kudos.dto.KudosDtos.WallDto;
import fr.epita.platform.apps.kudos.service.KudosService;
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

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Kudos", description = "Mur public de remerciements entre collègues, avec réactions.")
@RestController
@RequestMapping("/api/kudos")
@RequiredArgsConstructor
public class KudosController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";

    private final KudosService service;
    private final UserRepository users;

    @GetMapping("/wall")
    @Operation(summary = "Mur des kudos", description = """
            Accès : authentifié. Pagination **par curseur** (dérogation au §4.5) : du plus récent au plus ancien, \
            `limit` de 1 à 50 (20 par défaut) ; repasser `nextCursor` dans `cursor` pour la page suivante ; \
            `nextCursor` nul en fin de liste. Filtres `valueId`, `userId` (auteur ou destinataire). Les kudos modérés sont exclus.""")
    @ApiResponse(responseCode = "200", description = "Page du mur")
    @ApiResponse(responseCode = "400", description = "Curseur inconnu (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    public WallDto wall(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) UUID cursor,
                        @RequestParam(required = false) Integer limit, @RequestParam(required = false) UUID valueId,
                        @RequestParam(required = false) UUID userId) {
        return service.wall(me, cursor, limit, valueId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envoyer un kudo", description = "Accès : authentifié. 1 à 5 destinataires, jamais soi-même ; 5 kudos envoyés maximum par jour.")
    @ApiResponse(responseCode = "201", description = "Kudo publié")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Destinataire introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SELF_KUDO ou DAILY_LIMIT_REACHED")
    public KudoDto send(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody KudoRequest req) {
        return service.send(me, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer ou modérer un kudo", description = "Accès : auteur (dans l'heure qui suit l'envoi) ou ADMIN (modération : le kudo disparaît du mur et des classements).")
    @ApiResponse(responseCode = "204", description = "Kudo retiré")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Ni auteur ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Kudo introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Délai d'une heure dépassé (DELETE_WINDOW_EXPIRED)")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @PutMapping("/{id}/reactions/{emoji}")
    @Operation(summary = "Ajouter ma réaction", description = "Accès : authentifié. Idempotent. Emojis possibles : 👏 ❤️ 🎉 🚀 😂 (encodés dans l'URL). Une réaction de chaque type par utilisateur et par kudo.")
    @ApiResponse(responseCode = "200", description = "Kudo avec les réactions")
    @ApiResponse(responseCode = "400", description = "Emoji non autorisé (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Kudo introuvable (NOT_FOUND)")
    public KudoDto react(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @PathVariable String emoji) {
        return service.react(me, id, emoji, true);
    }

    @DeleteMapping("/{id}/reactions/{emoji}")
    @Operation(summary = "Retirer ma réaction", description = "Accès : authentifié. Idempotent.")
    @ApiResponse(responseCode = "200", description = "Kudo avec les réactions")
    @ApiResponse(responseCode = "400", description = "Emoji non autorisé (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Kudo introuvable (NOT_FOUND)")
    public KudoDto unreact(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @PathVariable String emoji) {
        return service.react(me, id, emoji, false);
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Profil kudos", description = "Accès : authentifié. Kudos reçus, envoyés, compteurs par valeur, 10 derniers reçus.")
    @ApiResponse(responseCode = "200", description = "Profil")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    public ProfileDto profile(@AuthenticationPrincipal AuthUser me, @PathVariable UUID userId) {
        return service.profile(me, userId);
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Classement du mois", description = "Accès : authentifié. Kudos reçus pendant `month` (AAAA-MM, mois courant par défaut), modérés exclus.")
    @ApiResponse(responseCode = "200", description = "Classement")
    @ApiResponse(responseCode = "400", description = "Mois invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RankDto> leaderboard(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.leaderboard(month);
    }

    @GetMapping("/values")
    @Operation(summary = "Valeurs de l'entreprise", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Valeurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ValueDto> values() {
        return service.values();
    }

    @PostMapping("/values")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une valeur", description = "Accès : ADMIN. Nom unique.")
    @ApiResponse(responseCode = "201", description = "Valeur ajoutée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "Valeur existante (VALUE_EXISTS)")
    public ValueDto addValue(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ValueRequest req) {
        return service.addValue(me, req.name());
    }

    @GetMapping("/candidates")
    @Operation(summary = "Destinataires possibles", description = "Accès : authentifié. Utilisateurs actifs.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> candidates() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
