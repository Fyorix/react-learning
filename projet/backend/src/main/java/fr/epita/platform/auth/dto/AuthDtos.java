package fr.epita.platform.auth.dto;

import fr.epita.platform.user.dto.UserDtos.UserDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(description = "Identifiants de connexion")
    public record LoginRequest(
            @Schema(description = "Adresse e-mail du compte, insensible à la casse", example = "admin@example.com")
            @NotBlank String email,
            @Schema(description = "Mot de passe", example = "change-me")
            @NotBlank String password) {
    }

    @Schema(description = "Demande de renouvellement des jetons")
    public record RefreshRequest(
            @Schema(description = "Jeton de rafraîchissement reçu à la connexion", example = "eyJhbGciOiJIUzI1NiJ9...")
            @NotBlank String refreshToken) {
    }

    @Schema(description = "Changement de mot de passe")
    public record ChangePasswordRequest(
            @Schema(description = "Mot de passe actuel", example = "ancien-mot-de-passe")
            @NotBlank String currentPassword,
            @Schema(description = "Nouveau mot de passe, 8 à 100 caractères", example = "nouveau-mot-de-passe")
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserDto user) {
    }
}
