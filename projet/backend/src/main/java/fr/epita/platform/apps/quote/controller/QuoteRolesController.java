package fr.epita.platform.apps.quote.controller;

import fr.epita.platform.apps.quote.service.QuoteService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Devis", description = "Catalogue de produits et devis envoyés aux clients, acceptés ou refusés.")
@RestController
@RequestMapping("/api/quotes/admin/roles")
public class QuoteRolesController extends AbstractRolesController {

    public QuoteRolesController(AppRoles roles) {
        super(roles, QuoteService.ROLES, Set.of(QuoteService.SALES));
    }
}
