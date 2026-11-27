package fr.epita.platform.common.relation;

import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Rôles propres à une application (technicien, relecteur, enseignant…) : table {@code <package>_roles (user_id, role)}.
 * Un ADMIN de la plateforme a implicitement tous les rôles.
 */
@Component
@RequiredArgsConstructor
public class AppRoles {

    private static final Pattern TABLE = Pattern.compile("[a-z_]+_roles");

    private final JdbcTemplate jdbc;
    private final UserLookup userLookup;

    public boolean has(String table, AuthUser user, String role) {
        return user.isAdmin() || hasExplicit(table, user.id(), role);
    }

    public boolean hasExplicit(String table, UUID userId, String role) {
        check(table);
        Integer n = jdbc.queryForObject("select count(*) from " + table + " where user_id = ? and role = ?",
                Integer.class, userId, role);
        return n != null && n > 0;
    }

    public void require(String table, AuthUser user, String role) {
        if (!has(table, user, role)) {
            throw ApiException.forbidden("Action réservée au rôle " + role);
        }
    }

    /** Rôles de l'utilisateur dans l'application ; un ADMIN les a tous. */
    public List<String> rolesOf(String table, AuthUser user, Set<String> allowed) {
        check(table);
        if (user.isAdmin()) {
            return allowed.stream().sorted().toList();
        }
        return jdbc.queryForList("select role from " + table + " where user_id = ? order by role", String.class, user.id());
    }

    public List<UUID> usersWith(String table, String role) {
        check(table);
        return jdbc.queryForList("select user_id from " + table + " where role = ?", UUID.class, role);
    }

    public List<RoleDto> list(String table) {
        check(table);
        List<Object[]> rows = jdbc.query("select user_id, role from " + table + " order by role",
                (rs, i) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2)});
        var names = userLookup.names(rows.stream().map(r -> (UUID) r[0]).toList());
        return rows.stream().map(r -> new RoleDto((UUID) r[0], names.get((UUID) r[0]), (String) r[1])).toList();
    }

    public RoleDto grant(String table, Set<String> allowed, UUID userId, String role) {
        check(table);
        if (!allowed.contains(role)) {
            throw fr.epita.platform.common.ValidationException.field("role", "Valeurs possibles : " + allowed);
        }
        userLookup.require(userId);
        if (!hasExplicit(table, userId, role)) {
            jdbc.update("insert into " + table + " (user_id, role) values (?, ?)", userId, role);
        }
        return new RoleDto(userId, userLookup.name(userId), role);
    }

    public void revoke(String table, UUID userId, String role) {
        check(table);
        if (jdbc.update("delete from " + table + " where user_id = ? and role = ?", userId, role) == 0) {
            throw ApiException.notFound("Rôle");
        }
    }

    private static void check(String table) {
        if (!TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("Table de rôles invalide : " + table);
        }
    }
}
