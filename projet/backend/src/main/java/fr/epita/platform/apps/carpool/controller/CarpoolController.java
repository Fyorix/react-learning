package fr.epita.platform.apps.carpool.controller;

import fr.epita.platform.apps.carpool.dto.CarpoolDtos.TripDto;
import fr.epita.platform.apps.carpool.dto.CarpoolDtos.TripRequest;
import fr.epita.platform.apps.carpool.service.CarpoolService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Covoiturage", description = "Publication de trajets et réservation de places.")
@RestController
@RequestMapping("/api/carpool")
@RequiredArgsConstructor
public class CarpoolController {

    private final CarpoolService service;

    @Operation(summary = "Publier un trajet", description = "Accès : authentifié (l'utilisateur devient conducteur). Départ dans le futur, 1 à 8 places.")
    @ApiResponse(responseCode = "201", description = "Trajet publié")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @PostMapping("/trips")
    @ResponseStatus(HttpStatus.CREATED)
    public TripDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TripRequest req) {
        return service.create(me, req);
    }

    @Operation(summary = "Rechercher des trajets", description = "Accès : authentifié. Trajets à venir et non annulés, filtrés par `from`, `to` (contient, insensible à la casse) et `date` (AAAA-MM-JJ). Pagination : `page` (à partir de 0) et `size` ; réponse `{content, page, size, totalElements, totalPages}`.")
    @ApiResponse(responseCode = "200", description = "Page de trajets")
    @ApiResponse(responseCode = "400", description = "Paramètre invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/trips")
    public PageResponse<TripDto> search(@AuthenticationPrincipal AuthUser me,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.search(me, from, to, date, page, size);
    }

    @Operation(summary = "Détail d'un trajet", description = "Accès : authentifié. Places restantes ; la liste des passagers n'est renvoyée qu'au conducteur et aux ADMIN.")
    @ApiResponse(responseCode = "200", description = "Trajet")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Trajet introuvable (NOT_FOUND)")
    @GetMapping("/trips/{id}")
    public TripDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @Operation(summary = "Modifier un trajet", description = "Accès : conducteur ou ADMIN. Le nombre de places ne peut pas descendre sous le nombre de réservations actives.")
    @ApiResponse(responseCode = "200", description = "Trajet modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Ni propriétaire ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Trajet introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SEATS_BELOW_BOOKINGS ou TRIP_CANCELLED")
    @PutMapping("/trips/{id}")
    public TripDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                          @Valid @RequestBody TripRequest req) {
        return service.update(me, id, req);
    }

    @Operation(summary = "Annuler un trajet", description = "Accès : conducteur ou ADMIN. Le trajet reste visible avec le statut CANCELLED.")
    @ApiResponse(responseCode = "200", description = "Trajet annulé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Ni propriétaire ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Trajet introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Trajet déjà annulé (TRIP_CANCELLED)")
    @PostMapping("/trips/{id}/cancel")
    public TripDto cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.cancel(me, id);
    }

    @Operation(summary = "Réserver une place", description = "Accès : authentifié. Une réservation par passager et par trajet ; le conducteur ne réserve pas son propre trajet ; jamais plus de réservations que de places, même en cas d'appels simultanés.")
    @ApiResponse(responseCode = "201", description = "Trajet avec la réservation")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Trajet introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "NO_SEAT_AVAILABLE, TRIP_NOT_BOOKABLE (passé ou annulé), ALREADY_BOOKED ou OWN_TRIP")
    @PostMapping("/trips/{id}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public TripDto book(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.book(me, id);
    }

    @Operation(summary = "Annuler sa réservation", description = "Accès : passager. Impossible sur un trajet passé.")
    @ApiResponse(responseCode = "200", description = "Trajet mis à jour")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Trajet ou réservation introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Trajet passé (TRIP_NOT_BOOKABLE)")
    @DeleteMapping("/trips/{id}/bookings/me")
    public TripDto cancelBooking(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.cancelBooking(me, id);
    }

    @Operation(summary = "Mes trajets", description = "Accès : authentifié. `role` = driver (trajets publiés) ou passenger (trajets réservés).")
    @ApiResponse(responseCode = "200", description = "Trajets")
    @ApiResponse(responseCode = "400", description = "Rôle inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/me/trips")
    public List<TripDto> myTrips(@AuthenticationPrincipal AuthUser me,
                                 @RequestParam(defaultValue = "driver") String role) {
        return service.myTrips(me, role);
    }

    @Operation(summary = "Trajets du mois", description = "Accès : ADMIN. `month` au format AAAA-MM (mois courant par défaut).")
    @ApiResponse(responseCode = "200", description = "Trajets")
    @ApiResponse(responseCode = "400", description = "Mois invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @GetMapping("/admin/trips")
    public List<TripDto> adminTrips(@AuthenticationPrincipal AuthUser me,
                                    @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.adminTrips(me, month);
    }
}
