package fr.epita.platform.apps.timesheet.controller;

import fr.epita.platform.apps.timesheet.service.TimesheetService;
import fr.epita.platform.common.relation.AbstractReportsController;
import fr.epita.platform.common.relation.ManagerRelations;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Feuilles de temps", description = "Saisie hebdomadaire du temps par projet et validation par le manager.")
@RestController
@RequestMapping("/api/timesheets/admin/reports")
public class TimesheetReportsController extends AbstractReportsController {

    public TimesheetReportsController(ManagerRelations relations) {
        super(relations, TimesheetService.REPORTS);
    }
}
