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
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

/**
 * Routes {@code /api/<préfixe>/admin/reports} communes aux applications qui ont une relation manager → salarié.
 * Chaque application déclare une sous-classe annotée {@code @RestController @RequestMapping(...)} et un {@code @Tag}.
 */
@PreAuthorize("hasRole('ADMIN')")
public abstract class AbstractReportsController {

    private final ManagerRelations relations;
    private final String table;

    protected AbstractReportsController(ManagerRelations relations, String table) {
        this.relations = relations;
        this.table = table;
    }

    @GetMapping
    @Operation(summary = "Relations manager → salariés", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "200", description = "Relations")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    public List<ReportDto> reports() {
        return relations.list(table);
    }

    @PutMapping("/{employeeId}")
    @Operation(summary = "Affecter un manager", description = "Accès : ADMIN. Un salarié a au plus un manager (remplacé s'il existe) ; pas son propre manager ; pas de cycle.")
    @ApiResponse(responseCode = "200", description = "Relation enregistrée")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SELF_MANAGER ou REPORTING_CYCLE")
    public ReportDto assign(@PathVariable UUID employeeId, @Valid @RequestBody AssignManagerRequest req) {
        return relations.assign(table, employeeId, req.managerId());
    }

    @DeleteMapping("/{employeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retirer le manager", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "204", description = "Relation supprimée")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Relation introuvable (NOT_FOUND)")
    public void remove(@PathVariable UUID employeeId) {
        relations.remove(table, employeeId);
    }
}
