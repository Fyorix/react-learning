package fr.epita.platform.apps.jargon.controller;

import fr.epita.platform.apps.jargon.service.JargonService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Jargon", description = "Glossaire collectif des acronymes : autocomplétion, définitions votées, modération.")
@RestController
@RequestMapping("/api/jargon/admin/roles")
public class JargonRolesController extends AbstractRolesController {

    public JargonRolesController(AppRoles roles) {
        super(roles, JargonService.ROLES, Set.of(JargonService.MODERATOR));
    }
}
