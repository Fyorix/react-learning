package fr.epita.platform.common.relation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Rôle propre à l'application détenu par un utilisateur")
public record RoleDto(UUID userId, String displayName, String role) {
}
