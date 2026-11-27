package fr.epita.platform.apps.crm.controller;

import fr.epita.platform.apps.crm.service.CrmService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "CRM", description = "Comptes, contacts, opportunités, pipeline par étape, interactions, relances et prévisions.")
@RestController
@RequestMapping("/api/crm/admin/roles")
public class CrmRolesController extends AbstractRolesController {

    public CrmRolesController(AppRoles roles) {
        super(roles, CrmService.ROLES, Set.of(CrmService.SALES));
    }
}
