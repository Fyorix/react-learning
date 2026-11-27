package fr.epita.platform.apps.appraisal.controller;

import fr.epita.platform.apps.appraisal.service.AppraisalService;
import fr.epita.platform.common.relation.AbstractReportsController;
import fr.epita.platform.common.relation.ManagerRelations;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Entretiens annuels", description = "Campagnes d'entretiens : auto-évaluation, évaluation du manager, objectifs et double signature.")
@RestController
@RequestMapping("/api/appraisals/admin/reports")
public class AppraisalReportsController extends AbstractReportsController {

    public AppraisalReportsController(ManagerRelations relations) {
        super(relations, AppraisalService.REPORTS);
    }
}
