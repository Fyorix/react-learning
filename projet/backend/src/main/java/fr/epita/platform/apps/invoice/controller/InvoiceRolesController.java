package fr.epita.platform.apps.invoice.controller;

import fr.epita.platform.apps.invoice.service.InvoiceService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Facturation", description = "Factures numérotées, paiements, avoirs et relances des retards.")
@RestController
@RequestMapping("/api/invoices/admin/roles")
public class InvoiceRolesController extends AbstractRolesController {

    public InvoiceRolesController(AppRoles roles) {
        super(roles, InvoiceService.ROLES, Set.of(InvoiceService.ACCOUNTANT));
    }
}
