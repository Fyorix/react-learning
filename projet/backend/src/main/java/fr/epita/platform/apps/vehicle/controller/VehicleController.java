package fr.epita.platform.apps.vehicle.controller;

import fr.epita.platform.apps.vehicle.dto.VehicleDtos.BookingDto;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.BookingRequest;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.CheckinRequest;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.CheckoutRequest;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.MaintenanceDto;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.VehicleDto;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.VehicleRequest;
import fr.epita.platform.apps.vehicle.service.VehicleService;
import fr.epita.platform.common.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Véhicules", description = "Réservation des véhicules de service, kilométrage et entretien.")
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String OWN = "Ni auteur de la réservation ni ADMIN (FORBIDDEN)";

    private final VehicleService service;

    @GetMapping
    @Operation(summary = "Véhicules", description = "Accès : authentifié. Avec `start` et `end` (ISO 8601) : uniquement les véhicules disponibles et libres sur la période.")
    @ApiResponse(responseCode = "200", description = "Véhicules")
    @ApiResponse(responseCode = "400", description = "Période incomplète (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<VehicleDto> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return service.list(start, end);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un véhicule", description = "Accès : ADMIN. Immatriculation unique.")
    @ApiResponse(responseCode = "201", description = "Véhicule ajouté")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Immatriculation existante (PLATE_EXISTS)")
    public VehicleDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody VehicleRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un véhicule", description = "Accès : ADMIN. Le kilométrage ne diminue jamais ; passer en MAINTENANCE ou RETIRED annule les réservations futures.")
    @ApiResponse(responseCode = "200", description = "Véhicule modifié")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Véhicule introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Immatriculation existante (PLATE_EXISTS)")
    public VehicleDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody VehicleRequest req) {
        return service.update(me, id, req);
    }

    @GetMapping("/{id}/bookings")
    @Operation(summary = "Planning d'un véhicule", description = "Accès : authentifié. Réservations non annulées entre `from` et `to` (facultatifs).")
    @ApiResponse(responseCode = "200", description = "Réservations")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Véhicule introuvable (NOT_FOUND)")
    public List<BookingDto> planning(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.planning(me, id, from, to);
    }

    @PostMapping("/{id}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Réserver un véhicule", description = "Accès : authentifié. Dans le futur, 5 jours maximum ; véhicule AVAILABLE ; aucun chevauchement, même en cas d'appels simultanés.")
    @ApiResponse(responseCode = "201", description = "Réservation créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Véhicule introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "VEHICLE_ALREADY_BOOKED ou VEHICLE_UNAVAILABLE")
    public BookingDto book(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody BookingRequest req) {
        return service.book(me, id, req);
    }

    @DeleteMapping("/bookings/{bookingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Annuler une réservation", description = "Accès : auteur ou ADMIN, avant le départ.")
    @ApiResponse(responseCode = "204", description = "Réservation annulée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = "Réservation introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà partie ou annulée (INVALID_STATUS_TRANSITION)")
    public void cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID bookingId) {
        service.cancel(me, bookingId);
    }

    @PostMapping("/bookings/{bookingId}/checkout")
    @Operation(summary = "Enregistrer le départ", description = "Accès : auteur ou ADMIN. Kilométrage au moins égal à celui connu du véhicule.")
    @ApiResponse(responseCode = "200", description = "Réservation (OUT)")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = "Réservation introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Statut incompatible (INVALID_STATUS_TRANSITION)")
    public BookingDto checkout(@AuthenticationPrincipal AuthUser me, @PathVariable UUID bookingId, @Valid @RequestBody CheckoutRequest req) {
        return service.checkout(me, bookingId, req.mileage());
    }

    @PostMapping("/bookings/{bookingId}/checkin")
    @Operation(summary = "Enregistrer le retour", description = "Accès : auteur ou ADMIN. Kilométrage au moins égal à celui du départ ; un problème BLOCKING passe le véhicule en MAINTENANCE et annule ses réservations futures.")
    @ApiResponse(responseCode = "200", description = "Réservation (RETURNED)")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = "Réservation introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Départ non enregistré (INVALID_STATUS_TRANSITION)")
    public BookingDto checkin(@AuthenticationPrincipal AuthUser me, @PathVariable UUID bookingId, @Valid @RequestBody CheckinRequest req) {
        return service.checkin(me, bookingId, req);
    }

    @GetMapping("/me/bookings")
    @Operation(summary = "Mes réservations", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Réservations")
    @ApiResponse(responseCode = "401", description = U401)
    public List<BookingDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/admin/maintenance")
    @Operation(summary = "Entretiens et problèmes", description = "Accès : ADMIN. Véhicules à entretenir (15 000 km depuis le dernier entretien) et problèmes non résolus.")
    @ApiResponse(responseCode = "200", description = "Suivi")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public MaintenanceDto maintenance(@AuthenticationPrincipal AuthUser me) {
        return service.maintenance(me);
    }

    @PostMapping("/{id}/service")
    @Operation(summary = "Enregistrer un entretien", description = "Accès : ADMIN. Remet le compteur d'entretien à zéro, clôt les problèmes et rend le véhicule disponible.")
    @ApiResponse(responseCode = "200", description = "Véhicule")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Véhicule introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Véhicule retiré (VEHICLE_UNAVAILABLE)")
    public VehicleDto service(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.service(me, id);
    }
}
