package fr.epita.platform.apps.association.controller;

import fr.epita.platform.apps.association.service.AssociationService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Association", description = "Adhérents, cotisations annuelles, activités avec liste d'attente, assemblée générale à vote secret.")
@RestController
@RequestMapping("/api/association/admin/roles")
public class AssociationRolesController extends AbstractRolesController {

    public AssociationRolesController(AppRoles roles) {
        super(roles, AssociationService.ROLES, Set.of(AssociationService.BOARD));
    }
}
