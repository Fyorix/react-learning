package fr.epita.platform.apps.helpdesk.controller;

import fr.epita.platform.apps.helpdesk.service.HelpdeskService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Helpdesk", description = "Tickets de support : ouverture, prise en charge par les techniciens, résolution.")
@RestController
@RequestMapping("/api/helpdesk/admin/roles")
public class HelpdeskRolesController extends AbstractRolesController {

    public HelpdeskRolesController(AppRoles roles) {
        super(roles, HelpdeskService.ROLES, Set.of(HelpdeskService.TECHNICIAN));
    }
}
