package fr.epita.platform.user.controller;

import fr.epita.platform.common.PageResponse;
import fr.epita.platform.user.dto.UserDtos.CreateUserRequest;
import fr.epita.platform.user.dto.UserDtos.UpdateUserRequest;
import fr.epita.platform.user.dto.UserDtos.UserDto;
import fr.epita.platform.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Users", description = "Gestion des comptes et des rôles (ADMIN uniquement).")
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Lister les utilisateurs", description = "Accès : ADMIN. `search` filtre sur l'e-mail ou le nom affiché ; tri par nom affiché. Pagination : `page` (à partir de 0) et `size` ; réponse `{content, page, size, totalElements, totalPages}`.")
    @ApiResponse(responseCode = "200", description = "Page d'utilisateurs")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @GetMapping
    public PageResponse<UserDto> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) String search) {
        return userService.list(search, page, size);
    }

    @Operation(summary = "Détail d'un utilisateur", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "200", description = "Utilisateur")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @Operation(summary = "Créer un utilisateur", description = "Accès : ADMIN. L'e-mail est stocké en minuscules et doit être unique.")
    @ApiResponse(responseCode = "201", description = "Utilisateur créé")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "E-mail déjà utilisé (EMAIL_ALREADY_USED)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return userService.create(req);
    }

    @Operation(summary = "Modifier un utilisateur", description = "Accès : ADMIN. Il doit toujours rester au moins un ADMIN actif : toute action qui ferait tomber ce nombre à zéro est refusée (409 LAST_ADMIN_REQUIRED). Un utilisateur qui détient du matériel (Inventaire) ne peut pas être désactivé (409 USER_HOLDS_EQUIPMENT).")
    @ApiResponse(responseCode = "200", description = "Utilisateur modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "LAST_ADMIN_REQUIRED, EMAIL_ALREADY_USED ou USER_HOLDS_EQUIPMENT")
    @PutMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return userService.update(id, req);
    }

    @Operation(summary = "Supprimer un utilisateur", description = "Accès : ADMIN. Il doit toujours rester au moins un ADMIN actif : toute action qui ferait tomber ce nombre à zéro est refusée (409 LAST_ADMIN_REQUIRED). Un utilisateur qui détient du matériel ne peut pas être supprimé (409 USER_HOLDS_EQUIPMENT).")
    @ApiResponse(responseCode = "204", description = "Utilisateur supprimé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "LAST_ADMIN_REQUIRED ou USER_HOLDS_EQUIPMENT")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }
}
