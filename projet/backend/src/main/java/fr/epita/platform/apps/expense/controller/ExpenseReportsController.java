package fr.epita.platform.apps.expense.controller;

import fr.epita.platform.apps.expense.service.ExpenseService;
import fr.epita.platform.common.relation.AbstractReportsController;
import fr.epita.platform.common.relation.ManagerRelations;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notes de frais", description = "Dépenses avec justificatif, notes mensuelles validées par le manager et remboursées par l'admin.")
@RestController
@RequestMapping("/api/expenses/admin/reports")
public class ExpenseReportsController extends AbstractReportsController {

    public ExpenseReportsController(ManagerRelations relations) {
        super(relations, ExpenseService.REPORTS);
    }
}
