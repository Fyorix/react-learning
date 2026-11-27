package fr.epita.platform.common;

import fr.epita.platform.user.entity.Role;

import java.util.UUID;

/** Utilisateur authentifié, reconstruit à partir du jeton JWT. */
public record AuthUser(UUID id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Propriétaire de la ressource ou ADMIN, sinon 403. */
    public void requireOwnerOrAdmin(UUID ownerId) {
        if (!isAdmin() && !id.equals(ownerId)) {
            throw ApiException.forbidden("Action réservée au propriétaire ou à un administrateur");
        }
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw ApiException.forbidden("Action réservée aux administrateurs");
        }
    }
}
