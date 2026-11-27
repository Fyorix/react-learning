package fr.epita.platform.user.dto;

import fr.epita.platform.user.entity.Role;
import fr.epita.platform.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserDto(UUID id, String email, String displayName, Role role, boolean enabled,
                          Instant createdAt, Instant updatedAt) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.isEnabled(),
                    u.getCreatedAt(), u.getUpdatedAt());
        }
    }

    /** Vue publique minimale d'un utilisateur (listes de choix dans les mini-applications). */
    public record UserSummary(UUID id, String displayName) {
        public static UserSummary of(User u) {
            return new UserSummary(u.getId(), u.getDisplayName());
        }
    }

    @Schema(description = "Création d'un utilisateur")
    public record CreateUserRequest(
            @Schema(description = "Adresse e-mail, unique (stockée en minuscules)", example = "alice@example.com")
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(description = "Mot de passe initial, 8 à 100 caractères", example = "motdepasse123")
            @NotBlank @Size(min = 8, max = 100) String password,
            @Schema(description = "Nom affiché, 100 caractères maximum", example = "Alice Martin")
            @NotBlank @Size(max = 100) String displayName,
            @Schema(description = "Rôle : ADMIN = accès étendu (gestion des utilisateurs, administration des applications), USER = accès standard", example = "USER")
            @NotNull Role role) {
    }

    @Schema(description = "Modification d'un utilisateur. Il doit toujours rester au moins un ADMIN actif.")
    public record UpdateUserRequest(
            @Schema(description = "Adresse e-mail, unique (stockée en minuscules)", example = "alice@example.com")
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(description = "Nom affiché, 100 caractères maximum", example = "Alice Martin")
            @NotBlank @Size(max = 100) String displayName,
            @Schema(description = "Rôle : ADMIN = accès étendu (gestion des utilisateurs, administration des applications), USER = accès standard. Rétrograder le dernier ADMIN actif est refusé (LAST_ADMIN_REQUIRED)", example = "USER")
            @NotNull Role role,
            @Schema(description = "Compte actif. Désactiver le dernier ADMIN actif est refusé (LAST_ADMIN_REQUIRED)", example = "true")
            @NotNull Boolean enabled) {
    }
}
