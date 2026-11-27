package fr.epita.platform.apps.ticketing.controller;

import fr.epita.platform.apps.ticketing.service.TicketingService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Billetterie", description = "Événements, tarifs et quotas, commandes, billets à code unique, contrôle à l'entrée.")
@RestController
@RequestMapping("/api/tickets/admin/roles")
public class TicketingRolesController extends AbstractRolesController {

    public TicketingRolesController(AppRoles roles) {
        super(roles, TicketingService.ROLES, Set.of(TicketingService.SCANNER));
    }
}
