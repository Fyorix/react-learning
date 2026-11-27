package fr.epita.platform.apps.inventory.controller;

import fr.epita.platform.apps.inventory.dto.InventoryDtos.AssignRequest;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.EquipmentDetailDto;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.EquipmentDto;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.EquipmentRequest;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.IssueDto;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.IssueRequest;
import fr.epita.platform.apps.inventory.dto.InventoryDtos.StatusRequest;
import fr.epita.platform.apps.inventory.entity.EquipmentStatus;
import fr.epita.platform.apps.inventory.entity.EquipmentType;
import fr.epita.platform.apps.inventory.service.InventoryService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Inventaire", description = "Inventaire du matériel, attributions, historique des détenteurs et signalements de panne.")
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService service;

    @Operation(summary = "Inventaire", description = "Accès : ADMIN. Filtres `type`, `status`, `search` (marque, modèle, numéro de série, détenteur). Pagination : `page` (à partir de 0) et `size` ; réponse `{content, page, size, totalElements, totalPages}`.")
    @ApiResponse(responseCode = "200", description = "Page d'équipements")
    @ApiResponse(responseCode = "400", description = "Filtre invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @GetMapping("/equipment")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<EquipmentDto> list(@RequestParam(required = false) EquipmentType type,
                                           @RequestParam(required = false) EquipmentStatus status,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return service.list(type, status, search, page, size);
    }

    @Operation(summary = "Détail d'un équipement", description = "Accès : ADMIN. Avec l'historique des détenteurs et des signalements.")
    @ApiResponse(responseCode = "200", description = "Équipement")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Équipement introuvable (NOT_FOUND)")
    @GetMapping("/equipment/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDetailDto detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @Operation(summary = "Ajouter un équipement", description = "Accès : ADMIN. Numéro de série unique ; l'équipement démarre DISPONIBLE.")
    @ApiResponse(responseCode = "201", description = "Équipement ajouté")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "Numéro de série déjà utilisé (SERIAL_NUMBER_ALREADY_USED)")
    @PostMapping("/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDto create(@Valid @RequestBody EquipmentRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Modifier un équipement", description = "Accès : ADMIN. Numéro de série unique.")
    @ApiResponse(responseCode = "200", description = "Équipement modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Équipement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SERIAL_NUMBER_ALREADY_USED ou EQUIPMENT_SCRAPPED")
    @PutMapping("/equipment/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDto update(@PathVariable UUID id, @Valid @RequestBody EquipmentRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Attribuer un équipement", description = "Accès : ADMIN. Seul un équipement DISPONIBLE peut être attribué, à un utilisateur actif. L'attribution est historisée.")
    @ApiResponse(responseCode = "200", description = "Équipement attribué")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Équipement ou utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "EQUIPMENT_NOT_AVAILABLE ou USER_DISABLED")
    @PostMapping("/equipment/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDto assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest req) {
        return service.assign(id, req.userId());
    }

    @Operation(summary = "Restituer un équipement", description = "Accès : ADMIN. ATTRIBUE → DISPONIBLE ; la restitution est historisée.")
    @ApiResponse(responseCode = "200", description = "Équipement restitué")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Équipement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "L'équipement n'est pas attribué (EQUIPMENT_NOT_ASSIGNED)")
    @PostMapping("/equipment/{id}/release")
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDto release(@PathVariable UUID id) {
        return service.release(id);
    }

    @Operation(summary = "Changer l'état", description = "Accès : ADMIN. Passer en EN_PANNE restitue automatiquement l'équipement et clôt ses signalements ; REBUT est définitif ; ATTRIBUE se fait via /assign.")
    @ApiResponse(responseCode = "200", description = "Équipement mis à jour")
    @ApiResponse(responseCode = "400", description = "Données invalides ou transition non autorisée (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Équipement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Équipement au rebut (EQUIPMENT_SCRAPPED)")
    @PatchMapping("/equipment/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentDto changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(id, req);
    }

    @Operation(summary = "Mon matériel", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Équipements détenus")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/me/equipment")
    public List<EquipmentDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @Operation(summary = "Signaler une panne", description = "Accès : détenteur actuel uniquement. Un seul signalement ouvert par équipement.")
    @ApiResponse(responseCode = "201", description = "Signalement créé")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Seul le détenteur actuel peut signaler (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Équipement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Signalement déjà ouvert (ISSUE_ALREADY_OPEN)")
    @PostMapping("/equipment/{id}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public IssueDto report(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                           @Valid @RequestBody IssueRequest req) {
        return service.report(me, id, req.description());
    }

    @Operation(summary = "Signalements à traiter", description = "Accès : ADMIN. `status` = open, resolved ou all.")
    @ApiResponse(responseCode = "200", description = "Signalements")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @GetMapping("/admin/issues")
    public List<IssueDto> adminIssues(@RequestParam(defaultValue = "open") String status) {
        return service.adminIssues(status);
    }
}
