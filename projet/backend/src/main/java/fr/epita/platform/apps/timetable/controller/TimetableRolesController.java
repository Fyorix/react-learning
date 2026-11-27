package fr.epita.platform.apps.timetable.controller;

import fr.epita.platform.apps.timetable.service.TimetableService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Emploi du temps", description = "Planning des groupes : salles, enseignants, matières, placement des cours sans conflit.")
@RestController
@RequestMapping("/api/timetable/admin/roles")
public class TimetableRolesController extends AbstractRolesController {

    public TimetableRolesController(AppRoles roles) {
        super(roles, TimetableService.ROLES, Set.of(TimetableService.TEACHER));
    }
}
