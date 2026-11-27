package fr.epita.platform.apps.directory.controller;

import fr.epita.platform.apps.directory.dto.DirectoryDtos.AssignmentRequest;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.PersonCardDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.PersonDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.ProfileRequest;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.SkillDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.SkillRequest;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.TeamDto;
import fr.epita.platform.apps.directory.dto.DirectoryDtos.TeamRequest;
import fr.epita.platform.apps.directory.service.DirectoryService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Annuaire", description = "Trombinoscope : recherche des collègues par équipe ou par compétence, fiches détaillées.")
@RestController
@RequestMapping("/api/directory")
@RequiredArgsConstructor
public class DirectoryController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";

    private final DirectoryService service;

    @GetMapping("/people")
    @Operation(summary = "Rechercher des collaborateurs", description = "Accès : authentifié. Utilisateurs actifs. `search` sur le nom et le poste (insensible à la casse et aux accents) ; filtres combinables `teamId`, `skillId` + `minLevel` (1 à 4), `location`. Tri par nom. Pagination : `page`, `size`.")
    @ApiResponse(responseCode = "200", description = "Page de cartes")
    @ApiResponse(responseCode = "400", description = "Filtre invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public PageResponse<PersonCardDto> search(@RequestParam(required = false) String search,
                                              @RequestParam(required = false) UUID teamId,
                                              @RequestParam(required = false) UUID skillId,
                                              @RequestParam(required = false) Integer minLevel,
                                              @RequestParam(required = false) String location,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "24") int size) {
        return service.search(search, teamId, skillId, minLevel, location, page, size);
    }

    @GetMapping("/people/{userId}")
    @Operation(summary = "Fiche détaillée", description = "Accès : authentifié. Équipe, manager, collègues de l'équipe, personnes managées, compétences par niveau décroissant.")
    @ApiResponse(responseCode = "200", description = "Fiche")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Collaborateur introuvable ou désactivé (NOT_FOUND)")
    public PersonDto get(@PathVariable UUID userId) {
        return service.get(userId);
    }

    @GetMapping("/people/{userId}/photo")
    @Operation(summary = "Photo d'un collaborateur", description = "Accès : authentifié (ajout au §5.21).")
    @ApiResponse(responseCode = "200", description = "Image")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Pas de photo (NOT_FOUND)")
    public ResponseEntity<byte[]> photo(@PathVariable UUID userId) {
        DirectoryService.Photo p = service.photo(userId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.contentType())).body(p.content());
    }

    @PutMapping("/people/me")
    @Operation(summary = "Modifier ma fiche", description = "Accès : authentifié. Poste, lieu, téléphone, bio, compétences (du référentiel, une fois chacune, niveau 1 à 4). L'équipe et le manager sont fixés par un ADMIN.")
    @ApiResponse(responseCode = "200", description = "Fiche")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public PersonDto updateMine(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ProfileRequest req) {
        return service.updateMine(me, req);
    }

    @PutMapping(value = "/people/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Changer ma photo", description = "Accès : authentifié. Multipart, fichier `file` : JPEG, PNG ou WebP, 5 Mo maximum.")
    @ApiResponse(responseCode = "200", description = "Fiche")
    @ApiResponse(responseCode = "400", description = "Fichier refusé (INVALID_FILE)")
    @ApiResponse(responseCode = "401", description = U401)
    public PersonDto updatePhoto(@AuthenticationPrincipal AuthUser me, @RequestPart("file") MultipartFile file) {
        return service.updatePhoto(me, file);
    }

    @PutMapping("/people/{userId}/assignment")
    @Operation(summary = "Affecter équipe et manager", description = "Accès : ADMIN. Un collaborateur n'est pas son propre manager.")
    @ApiResponse(responseCode = "200", description = "Fiche")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Collaborateur, équipe ou manager introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Propre manager (SELF_MANAGER)")
    public PersonDto assign(@AuthenticationPrincipal AuthUser me, @PathVariable UUID userId, @Valid @RequestBody AssignmentRequest req) {
        return service.assign(me, userId, req);
    }

    @GetMapping("/teams")
    @Operation(summary = "Équipes", description = "Accès : authentifié. Avec l'effectif (collaborateurs actifs).")
    @ApiResponse(responseCode = "200", description = "Équipes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<TeamDto> teams() {
        return service.teams();
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une équipe", description = "Accès : ADMIN. Nom unique.")
    @ApiResponse(responseCode = "201", description = "Équipe créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Équipe existante (TEAM_EXISTS)")
    public TeamDto createTeam(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TeamRequest req) {
        return service.createTeam(me, req.name());
    }

    @GetMapping("/skills")
    @Operation(summary = "Référentiel de compétences", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Compétences")
    @ApiResponse(responseCode = "401", description = U401)
    public List<SkillDto> skills() {
        return service.skills();
    }

    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une compétence", description = "Accès : ADMIN. Nom unique.")
    @ApiResponse(responseCode = "201", description = "Compétence ajoutée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Compétence existante (SKILL_EXISTS)")
    public SkillDto createSkill(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SkillRequest req) {
        return service.createSkill(me, req);
    }
}
