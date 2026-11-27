package fr.epita.platform.apps.leave.controller;

import fr.epita.platform.apps.leave.service.LeaveService;
import fr.epita.platform.common.relation.AbstractReportsController;
import fr.epita.platform.common.relation.ManagerRelations;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Congés", description = "Demandes de congés, validation par le manager et soldes de jours.")
@RestController
@RequestMapping("/api/leaves/admin/reports")
public class LeaveReportsController extends AbstractReportsController {

    public LeaveReportsController(ManagerRelations relations) {
        super(relations, LeaveService.REPORTS);
    }
}
