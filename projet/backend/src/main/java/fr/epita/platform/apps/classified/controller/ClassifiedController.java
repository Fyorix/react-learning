package fr.epita.platform.apps.classified.controller;

import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ConversationDto;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ListingDto;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ListingRequest;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ListingSummaryDto;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.MessageRequest;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ReportRequest;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ReportedDto;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.StatusRequest;
import fr.epita.platform.apps.classified.entity.ClassifiedEnums.ListingType;
import fr.epita.platform.apps.classified.service.ClassifiedService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Tag(name = "Annonces", description = "Petites annonces entre collègues (vente, don, service) avec photos et messagerie.")
@RestController
@RequestMapping("/api/classifieds")
@RequiredArgsConstructor
public class ClassifiedController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides (VALIDATION_ERROR) ou photos refusées : 1 à 6 images JPEG, PNG ou WebP de 5 Mo maximum (INVALID_FILE)";
    private static final String AUTHOR = "Pas l'auteur de l'annonce (FORBIDDEN)";
    private static final String NF = "Annonce introuvable (NOT_FOUND)";

    private final ClassifiedService service;

    @GetMapping
    @Operation(summary = "Annonces disponibles", description = "Accès : authentifié. Annonces ACTIVE, non expirées et non masquées ; filtres `type`, `category`, `minPrice`, `maxPrice`, `search`. Pagination : `page`, `size`.")
    @ApiResponse(responseCode = "200", description = "Page d'annonces")
    @ApiResponse(responseCode = "400", description = "Filtre invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public PageResponse<ListingSummaryDto> search(@RequestParam(required = false) ListingType type,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) BigDecimal minPrice,
                                                  @RequestParam(required = false) BigDecimal maxPrice,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.search(type, category, minPrice, maxPrice, search, page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publier une annonce", description = "Accès : authentifié. Multipart : `data` (JSON) et 1 à 6 fichiers `photos` (la première est la couverture). 10 annonces actives maximum par personne ; l'annonce expire au bout de 30 jours.")
    @ApiResponse(responseCode = "201", description = "Annonce publiée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "409", description = "Trop d'annonces actives (TOO_MANY_LISTINGS)")
    public ListingDto publish(@AuthenticationPrincipal AuthUser me, @Valid @RequestPart("data") ListingRequest data,
                              @RequestPart("photos") List<MultipartFile> photos) {
        return service.publish(me, data, photos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une annonce", description = "Accès : authentifié. Une annonce masquée (signalée) n'est visible que de son auteur et des ADMIN.")
    @ApiResponse(responseCode = "200", description = "Annonce")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = NF)
    public ListingDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une annonce", description = "Accès : auteur. Mêmes règles qu'à la publication (prix selon le type).")
    @ApiResponse(responseCode = "200", description = "Annonce modifiée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = AUTHOR)
    @ApiResponse(responseCode = "404", description = NF)
    public ListingDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ListingRequest req) {
        return service.update(me, id, req);
    }

    @PutMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Remplacer les photos", description = "Accès : auteur. Multipart, 1 à 6 fichiers `photos` dans l'ordre voulu (la première est la couverture).")
    @ApiResponse(responseCode = "200", description = "Annonce")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = AUTHOR)
    @ApiResponse(responseCode = "404", description = NF)
    public ListingDto photos(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @RequestPart("photos") List<MultipartFile> photos) {
        return service.updatePhotos(me, id, photos);
    }

    @GetMapping("/{id}/photos/{photoId}")
    @Operation(summary = "Image d'une annonce", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Image")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Annonce ou photo introuvable (NOT_FOUND)")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @PathVariable UUID photoId) {
        ClassifiedService.Image img = service.photo(id, photoId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.contentType())).body(img.content());
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut", description = "Accès : auteur. ACTIVE, RESERVED ou CLOSED ; une annonce close ou expirée ne change plus de statut (la renouveler).")
    @ApiResponse(responseCode = "200", description = "Annonce")
    @ApiResponse(responseCode = "400", description = "Statut invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = AUTHOR)
    @ApiResponse(responseCode = "404", description = NF)
    @ApiResponse(responseCode = "409", description = "Annonce close ou expirée (INVALID_STATUS_TRANSITION)")
    public ListingDto status(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(me, id, req.status());
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Renouveler 30 jours", description = "Accès : auteur. Repousse l'expiration à aujourd'hui + 30 jours ; une annonce expirée redevient active (dans la limite de 10).")
    @ApiResponse(responseCode = "200", description = "Annonce renouvelée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = AUTHOR)
    @ApiResponse(responseCode = "404", description = NF)
    @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION (annonce close) ou TOO_MANY_LISTINGS")
    public ListingDto renew(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.renew(me, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une annonce", description = "Accès : auteur ou ADMIN.")
    @ApiResponse(responseCode = "204", description = "Annonce supprimée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Ni auteur ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = NF)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Contacter l'auteur ou répondre", description = "Accès : authentifié. Sans `toUserId`, le message va à l'auteur (pas sa propre annonce, annonce disponible). L'auteur répond avec `toUserId` = l'interlocuteur. Chaque fil n'est visible que des deux participants.")
    @ApiResponse(responseCode = "201", description = "Conversation mise à jour")
    @ApiResponse(responseCode = "400", description = "Données invalides (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = NF)
    @ApiResponse(responseCode = "409", description = "OWN_LISTING ou LISTING_UNAVAILABLE")
    public ConversationDto message(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody MessageRequest req) {
        return service.message(me, id, req);
    }

    @GetMapping("/me/conversations")
    @Operation(summary = "Mes conversations", description = "Accès : authentifié. Un fil par annonce et par interlocuteur, le plus récent d'abord.")
    @ApiResponse(responseCode = "200", description = "Conversations")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ConversationDto> conversations(@AuthenticationPrincipal AuthUser me) {
        return service.conversations(me);
    }

    @GetMapping("/me/listings")
    @Operation(summary = "Mes annonces", description = "Accès : authentifié. Tous statuts confondus.")
    @ApiResponse(responseCode = "200", description = "Annonces")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ListingSummaryDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @PostMapping("/{id}/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Signaler une annonce", description = "Accès : authentifié, une fois par annonce, pas la sienne. À 3 signalements de personnes différentes, l'annonce est masquée jusqu'à la décision d'un ADMIN.")
    @ApiResponse(responseCode = "204", description = "Signalement enregistré")
    @ApiResponse(responseCode = "400", description = "Données invalides (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = NF)
    @ApiResponse(responseCode = "409", description = "ALREADY_REPORTED ou OWN_LISTING")
    public void report(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ReportRequest req) {
        service.report(me, id, req.reason());
    }

    @GetMapping("/admin/reports")
    @Operation(summary = "Annonces signalées", description = "Accès : ADMIN. Avec les motifs et l'état masqué.")
    @ApiResponse(responseCode = "200", description = "Annonces signalées")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    public List<ReportedDto> reported(@AuthenticationPrincipal AuthUser me) {
        return service.reported(me);
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Rétablir une annonce", description = "Accès : ADMIN. Annule le masquage et efface les signalements (ajout au §5.25 ; la suppression se fait par DELETE).")
    @ApiResponse(responseCode = "200", description = "Annonce rétablie")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = NF)
    public ListingDto restore(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.restore(me, id);
    }
}
