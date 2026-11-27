package fr.epita.platform.apps.attendance.controller;

import fr.epita.platform.apps.attendance.service.AttendanceService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Absences", description = "Appel des séances, justificatifs d'absence, compteurs et alertes.")
@RestController
@RequestMapping("/api/attendance/admin/roles")
public class AttendanceRolesController extends AbstractRolesController {

    public AttendanceRolesController(AppRoles roles) {
        super(roles, AttendanceService.ROLES, Set.of(AttendanceService.TEACHER));
    }
}
