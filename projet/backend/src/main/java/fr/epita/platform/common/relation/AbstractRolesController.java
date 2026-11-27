package fr.epita.platform.common.relation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Routes {@code /api/<préfixe>/admin/roles} communes aux applications qui ont des rôles propres. */
@PreAuthorize("hasRole('ADMIN')")
public abstract class AbstractRolesController {

    private final AppRoles roles;
    private final String table;
    private final Set<String> allowed;

    protected AbstractRolesController(AppRoles roles, String table, Set<String> allowed) {
        this.roles = roles;
        this.table = table;
        this.allowed = allowed;
    }

    @GetMapping
    @Operation(summary = "Rôles propres à l'application", description = "Accès : ADMIN. Un ADMIN a implicitement tous les rôles.")
    @ApiResponse(responseCode = "200", description = "Rôles attribués")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    public List<RoleDto> roles() {
        return roles.list(table);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Attribuer un rôle", description = "Accès : ADMIN. Idempotent. Les rôles possibles dépendent de l'application (voir sa description).")
    @ApiResponse(responseCode = "200", description = "Rôle attribué")
    @ApiResponse(responseCode = "400", description = "Rôle inconnu (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    public RoleDto grant(@PathVariable UUID userId, @Valid @RequestBody GrantRoleRequest req) {
        return roles.grant(table, allowed, userId, req.role());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retirer un rôle", description = "Accès : ADMIN. `role` en paramètre de requête.")
    @ApiResponse(responseCode = "204", description = "Rôle retiré")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Rôle introuvable (NOT_FOUND)")
    public void revoke(@PathVariable UUID userId, @RequestParam String role) {
        roles.revoke(table, userId, role);
    }
}
