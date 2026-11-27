package fr.epita.platform.apps.fitness.controller;

import fr.epita.platform.apps.fitness.service.FitnessService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Cours de sport", description = "Cours collectifs réservables avec 8 crédits par mois, liste d'attente et annulations.")
@RestController
@RequestMapping("/api/fitness/admin/roles")
public class FitnessRolesController extends AbstractRolesController {

    public FitnessRolesController(AppRoles roles) {
        super(roles, FitnessService.ROLES, Set.of(FitnessService.COACH));
    }
}
