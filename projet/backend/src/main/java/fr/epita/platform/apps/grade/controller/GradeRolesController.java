package fr.epita.platform.apps.grade.controller;

import fr.epita.platform.apps.grade.service.GradeService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Notes & bulletins", description = "Saisie des notes, moyennes pondérées, bulletins et verrouillage des périodes.")
@RestController
@RequestMapping("/api/grades/admin/roles")
public class GradeRolesController extends AbstractRolesController {

    public GradeRolesController(AppRoles roles) {
        super(roles, GradeService.ROLES, Set.of(GradeService.TEACHER));
    }
}
