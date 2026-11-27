package fr.epita.platform.apps.recruitment.controller;

import fr.epita.platform.apps.recruitment.service.RecruitmentService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Recrutement", description = "Postes, pipeline des candidatures, évaluations d'entretien et statistiques.")
@RestController
@RequestMapping("/api/recruitment/admin/roles")
public class RecruitmentRolesController extends AbstractRolesController {

    public RecruitmentRolesController(AppRoles roles) {
        super(roles, RecruitmentService.ROLES, Set.of(RecruitmentService.RECRUITER));
    }
}
