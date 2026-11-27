package fr.epita.platform.apps.room.controller;

import fr.epita.platform.apps.room.dto.RoomDtos.BookingDto;
import fr.epita.platform.apps.room.dto.RoomDtos.BookingRequest;
import fr.epita.platform.apps.room.dto.RoomDtos.OccupancyDto;
import fr.epita.platform.apps.room.dto.RoomDtos.RoomDto;
import fr.epita.platform.apps.room.dto.RoomDtos.RoomRequest;
import fr.epita.platform.apps.room.entity.Equipment;
import fr.epita.platform.apps.room.service.RoomService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Salles", description = "Réservation de salles de réunion par créneaux de 30 minutes.")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String SLOT = "Créneau : aligné sur 30 minutes, même jour, entre 8 h et 20 h (heure de Paris), de 30 min à 4 h.";

    private final RoomService service;

    @GetMapping
    @Operation(summary = "Lister les salles", description = "Accès : authentifié. Salles actives (ADMIN : `includeInactive=true` pour toutes), filtres `equipment` et `minCapacity`.")
    @ApiResponse(responseCode = "200", description = "Salles")
    @ApiResponse(responseCode = "400", description = "Filtre invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RoomDto> list(@RequestParam(required = false) Equipment equipment,
                              @RequestParam(required = false) Integer minCapacity,
                              @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(equipment, minCapacity, includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une salle", description = "Accès : ADMIN. Nom unique.")
    @ApiResponse(responseCode = "201", description = "Salle créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "Nom déjà utilisé (ROOM_NAME_EXISTS)")
    public RoomDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody RoomRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une salle", description = "Accès : ADMIN. Désactiver une salle (`active=false`) annule ses réservations futures.")
    @ApiResponse(responseCode = "200", description = "Salle modifiée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Salle introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Nom déjà utilisé (ROOM_NAME_EXISTS)")
    public RoomDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RoomRequest req) {
        return service.update(me, id, req);
    }

    @GetMapping("/{id}/bookings")
    @Operation(summary = "Planning d'une salle", description = "Accès : authentifié. Réservations du jour `date` (AAAA-MM-JJ, aujourd'hui par défaut).")
    @ApiResponse(responseCode = "200", description = "Réservations du jour")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Salle introuvable (NOT_FOUND)")
    public List<BookingDto> day(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.dayBookings(me, id, date);
    }

    @GetMapping("/available")
    @Operation(summary = "Salles libres sur un créneau", description = "Accès : authentifié. " + SLOT + " Filtres `attendees` (capacité minimale) et `equipment` (tous requis).")
    @ApiResponse(responseCode = "200", description = "Salles libres")
    @ApiResponse(responseCode = "400", description = "Créneau invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RoomDto> available(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
                                   @RequestParam(required = false) Integer attendees,
                                   @RequestParam(required = false) Set<Equipment> equipment) {
        return service.available(start, end, attendees, equipment);
    }

    @PostMapping("/{id}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Réserver une salle", description = "Accès : authentifié. " + SLOT + " Pas dans le passé ni à plus de 30 jours ; participants ≤ capacité ; jamais deux réservations qui se chevauchent sur une salle, même en cas d'appels simultanés.")
    @ApiResponse(responseCode = "201", description = "Réservation créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Salle introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "ROOM_ALREADY_BOOKED, BOOKING_OUT_OF_RANGE, CAPACITY_EXCEEDED ou ROOM_INACTIVE")
    public BookingDto book(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody BookingRequest req) {
        return service.book(me, id, req);
    }

    @PutMapping("/bookings/{bookingId}")
    @Operation(summary = "Modifier une réservation", description = "Accès : auteur ou ADMIN. Mêmes règles qu'à la création.")
    @ApiResponse(responseCode = "200", description = "Réservation modifiée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Ni auteur ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Réservation introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "ROOM_ALREADY_BOOKED, BOOKING_OUT_OF_RANGE, CAPACITY_EXCEEDED ou ROOM_INACTIVE")
    public BookingDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID bookingId,
                             @Valid @RequestBody BookingRequest req) {
        return service.update(me, bookingId, req);
    }

    @DeleteMapping("/bookings/{bookingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Annuler une réservation", description = "Accès : auteur ou ADMIN.")
    @ApiResponse(responseCode = "204", description = "Réservation annulée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Ni auteur ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Réservation introuvable (NOT_FOUND)")
    public void cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID bookingId) {
        service.cancel(me, bookingId);
    }

    @GetMapping("/me/bookings")
    @Operation(summary = "Mes réservations à venir", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Réservations")
    @ApiResponse(responseCode = "401", description = U401)
    public List<BookingDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/admin/occupancy")
    @Operation(summary = "Taux d'occupation", description = "Accès : ADMIN. Heures réservées / heures d'ouverture (12 h par jour ouvré) entre `from` et `to` (mois courant par défaut).")
    @ApiResponse(responseCode = "200", description = "Occupation par salle")
    @ApiResponse(responseCode = "400", description = "Période invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    public List<OccupancyDto> occupancy(@AuthenticationPrincipal AuthUser me,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.occupancy(me, from, to);
    }
}
