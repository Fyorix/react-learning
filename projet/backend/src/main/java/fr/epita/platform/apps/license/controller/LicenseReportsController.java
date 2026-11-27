package fr.epita.platform.apps.license.controller;

import fr.epita.platform.apps.license.service.LicenseService;
import fr.epita.platform.common.relation.AbstractReportsController;
import fr.epita.platform.common.relation.ManagerRelations;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Licences", description = "Abonnements logiciels : sièges, demandes de licence, alertes et coûts.")
@RestController
@RequestMapping("/api/licenses/admin/reports")
public class LicenseReportsController extends AbstractReportsController {

    public LicenseReportsController(ManagerRelations relations) {
        super(relations, LicenseService.REPORTS);
    }
}
