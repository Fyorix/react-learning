package fr.epita.platform.apps.defense.controller;

import fr.epita.platform.apps.defense.service.DefenseService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Soutenances", description = "Planning des soutenances : créneaux, salles, jurys, choix des groupes et notes.")
@RestController
@RequestMapping("/api/defenses/admin/roles")
public class DefenseRolesController extends AbstractRolesController {

    public DefenseRolesController(AppRoles roles) {
        super(roles, DefenseService.ROLES, Set.of(DefenseService.TEACHER));
    }
}
