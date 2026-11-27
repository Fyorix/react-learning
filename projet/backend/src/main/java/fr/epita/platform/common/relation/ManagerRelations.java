package fr.epita.platform.common.relation;

import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.UserLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Relation manager → salarié propre à chaque application (convention des projets 5.12 à 5.56) :
 * table {@code <package>_reports (manager_id, employee_id)}, un manager au plus par salarié, sans cycle.
 */
@Component
@RequiredArgsConstructor
public class ManagerRelations {

    private static final Pattern TABLE = Pattern.compile("[a-z_]+_reports");

    private final JdbcTemplate jdbc;
    private final UserLookup userLookup;

    public Optional<UUID> managerOf(String table, UUID employeeId) {
        check(table);
        return jdbc.queryForList("select manager_id from " + table + " where employee_id = ?", UUID.class, employeeId)
                .stream().findFirst();
    }

    public List<UUID> employeesOf(String table, UUID managerId) {
        check(table);
        return jdbc.queryForList("select employee_id from " + table + " where manager_id = ?", UUID.class, managerId);
    }

    public boolean isManagerOf(String table, UUID managerId, UUID employeeId) {
        return managerOf(table, employeeId).map(managerId::equals).orElse(false);
    }

    public List<ReportDto> list(String table) {
        check(table);
        List<UUID[]> rows = jdbc.query("select employee_id, manager_id from " + table,
                (rs, i) -> new UUID[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)});
        var names = userLookup.names(rows.stream().flatMap(r -> java.util.stream.Stream.of(r[0], r[1])).toList());
        return rows.stream().map(r -> new ReportDto(r[0], names.get(r[0]), r[1], names.get(r[1])))
                .sorted(java.util.Comparator.comparing(ReportDto::employeeName,
                        java.util.Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public ReportDto assign(String table, UUID employeeId, UUID managerId) {
        check(table);
        userLookup.require(employeeId);
        userLookup.require(managerId);
        if (employeeId.equals(managerId)) {
            throw ApiException.conflict("SELF_MANAGER", "Un utilisateur ne peut pas être son propre manager");
        }
        // pas de cycle : le futur manager ne doit pas être (indirectement) sous le salarié
        Set<UUID> seen = new HashSet<>();
        UUID current = managerId;
        while (current != null && seen.add(current)) {
            if (current.equals(employeeId)) {
                throw ApiException.conflict("REPORTING_CYCLE", "Cette affectation créerait un cycle hiérarchique");
            }
            current = managerOf(table, current).orElse(null);
        }
        jdbc.update("delete from " + table + " where employee_id = ?", employeeId);
        jdbc.update("insert into " + table + " (manager_id, employee_id) values (?, ?)", managerId, employeeId);
        return new ReportDto(employeeId, userLookup.name(employeeId), managerId, userLookup.name(managerId));
    }

    public void remove(String table, UUID employeeId) {
        check(table);
        if (jdbc.update("delete from " + table + " where employee_id = ?", employeeId) == 0) {
            throw ApiException.notFound("Relation");
        }
    }

    private static void check(String table) {
        if (!TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("Table de relation invalide : " + table);
        }
    }
}
