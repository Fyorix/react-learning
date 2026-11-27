package fr.epita.platform.auth.controller;

import fr.epita.platform.auth.dto.AuthDtos.ChangePasswordRequest;
import fr.epita.platform.auth.dto.AuthDtos.LoginRequest;
import fr.epita.platform.auth.dto.AuthDtos.RefreshRequest;
import fr.epita.platform.auth.dto.AuthDtos.TokenResponse;
import fr.epita.platform.auth.service.AuthService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.dto.UserDtos.UserDto;
import fr.epita.platform.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@Tag(name = "Auth", description = "Connexion, renouvellement des jetons JWT et profil de l'utilisateur connecté.")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "Se connecter", description = "Accès : public. L'e-mail est insensible à la casse. Renvoie un jeton d'accès (durée `expiresIn` en secondes) et un jeton de rafraîchissement.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Connexion réussie")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "E-mail ou mot de passe incorrect (BAD_CREDENTIALS)")
    @ApiResponse(responseCode = "403", description = "Compte désactivé (ACCOUNT_DISABLED)")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.email(), req.password());
    }

    @Operation(summary = "Renouveler les jetons", description = "Accès : public. Échange un jeton de rafraîchissement valide contre un nouveau couple de jetons. Un jeton d'accès est refusé.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Nouveau couple de jetons")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton invalide, expiré ou compte désactivé (INVALID_TOKEN)")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @Operation(summary = "Profil de l'utilisateur connecté", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Profil")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return authService.me(me.id());
    }

    @Operation(summary = "Changer son mot de passe", description = "Accès : authentifié. Le mot de passe actuel doit être fourni ; le nouveau fait entre 8 et 100 caractères.")
    @ApiResponse(responseCode = "204", description = "Mot de passe modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides (VALIDATION_ERROR) ou mot de passe actuel incorrect (INVALID_PASSWORD)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(me.id(), req.currentPassword(), req.newPassword());
    }
}
