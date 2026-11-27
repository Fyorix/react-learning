package fr.epita.platform.apps.kb.controller;

import fr.epita.platform.apps.kb.service.KbService;
import fr.epita.platform.common.relation.AbstractRolesController;
import fr.epita.platform.common.relation.AppRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Base de connaissances", description = "Wiki interne : articles Markdown versionnés, relecture avant publication, avis.")
@RestController
@RequestMapping("/api/kb/admin/roles")
public class KbRolesController extends AbstractRolesController {

    public KbRolesController(AppRoles roles) {
        super(roles, KbService.ROLES, Set.of(KbService.REVIEWER));
    }
}
