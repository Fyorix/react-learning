package fr.epita.platform.apps.access.controller;

import fr.epita.platform.apps.access.service.AccessService;
import fr.epita.platform.common.relation.AbstractReportsController;
import fr.epita.platform.common.relation.ManagerRelations;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Demandes d'accès", description = "Droits sur les outils internes : demande, double validation, durée, revue et audit.")
@RestController
@RequestMapping("/api/access/admin/reports")
public class AccessReportsController extends AbstractReportsController {

    public AccessReportsController(ManagerRelations relations) {
        super(relations, AccessService.REPORTS);
    }
}
