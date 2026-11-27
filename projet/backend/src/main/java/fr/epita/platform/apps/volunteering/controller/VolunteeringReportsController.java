package fr.epita.platform.apps.volunteering.controller;

import fr.epita.platform.apps.volunteering.service.VolunteeringService;
import fr.epita.platform.common.relation.AbstractReportsController;
import fr.epita.platform.common.relation.ManagerRelations;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bénévolat", description = "Missions solidaires auprès d'associations partenaires, validation manager, plafond annuel et bilan.")
@RestController
@RequestMapping("/api/volunteering/admin/reports")
public class VolunteeringReportsController extends AbstractReportsController {

    public VolunteeringReportsController(ManagerRelations relations) {
        super(relations, VolunteeringService.REPORTS);
    }
}
