package fr.epita.platform.apps.training.controller;

import fr.epita.platform.apps.training.service.TrainingService;
import fr.epita.platform.common.relation.AbstractReportsController;
import fr.epita.platform.common.relation.ManagerRelations;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Formations", description = "Catalogue de formations internes, inscriptions validées par le manager, émargement et attestations.")
@RestController
@RequestMapping("/api/training/admin/reports")
public class TrainingReportsController extends AbstractReportsController {

    public TrainingReportsController(ManagerRelations relations) {
        super(relations, TrainingService.REPORTS);
    }
}
