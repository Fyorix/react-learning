package fr.epita.platform.apps.supply.controller;

import fr.epita.platform.apps.supply.service.SupplyService;
import fr.epita.platform.common.relation.AbstractReportsController;
import fr.epita.platform.common.relation.ManagerRelations;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fournitures", description = "Catalogue de fournitures, demandes, préparation, stock et réapprovisionnements.")
@RestController
@RequestMapping("/api/supplies/admin/reports")
public class SupplyReportsController extends AbstractReportsController {

    public SupplyReportsController(ManagerRelations relations) {
        super(relations, SupplyService.REPORTS);
    }
}
