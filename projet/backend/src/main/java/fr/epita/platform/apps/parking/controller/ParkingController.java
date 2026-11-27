package fr.epita.platform.apps.parking.controller;

import fr.epita.platform.apps.parking.dto.ParkingDtos.BookRequest;
import fr.epita.platform.apps.parking.dto.ParkingDtos.DayDto;
import fr.epita.platform.apps.parking.dto.ParkingDtos.HolderRequest;
import fr.epita.platform.apps.parking.dto.ParkingDtos.MeDto;
import fr.epita.platform.apps.parking.dto.ParkingDtos.MyBookingDto;
import fr.epita.platform.apps.parking.dto.ParkingDtos.ReleaseRequest;
import fr.epita.platform.apps.parking.dto.ParkingDtos.SpotDto;
import fr.epita.platform.apps.parking.dto.ParkingDtos.SpotRequest;
import fr.epita.platform.apps.parking.dto.ParkingDtos.UsageDto;
import fr.epita.platform.apps.parking.service.ParkingService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.dto.UserDtos.UserSummary;
import fr.epita.platform.user.repository.UserRepository;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Parking", description = "Partage des places de parking : libération par les titulaires, réservation à la journée.")
@RestController
@RequestMapping("/api/parking")
@RequiredArgsConstructor
public class ParkingController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";

    private final ParkingService service;
    private final UserRepository users;

    @GetMapping("/days/{date}")
    @Operation(summary = "Situation d'un jour", description = "Accès : authentifié. Pour chaque place : HOLDER (titulaire présent), RELEASED (libérée et libre), FREE (sans titulaire et libre) ou BOOKED (réservée), avec l'occupant.")
    @ApiResponse(responseCode = "200", description = "Situation")
    @ApiResponse(responseCode = "400", description = "Date invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public DayDto day(@AuthenticationPrincipal AuthUser me, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.day(me, date);
    }

    @GetMapping("/me")
    @Operation(summary = "Ma situation", description = "Accès : authentifié. Ma place (si titulaire), mes libérations à venir (et celles déjà réservées par un collègue), mes réservations.")
    @ApiResponse(responseCode = "200", description = "Situation")
    @ApiResponse(responseCode = "401", description = U401)
    public MeDto me(@AuthenticationPrincipal AuthUser me) {
        return service.me(me);
    }

    @PostMapping("/me/releases")
    @Operation(summary = "Libérer ma place", description = "Accès : titulaire. Jours ouvrés à venir uniquement ; les jours déjà libérés sont ignorés.")
    @ApiResponse(responseCode = "200", description = "Ma situation")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas titulaire d'une place (FORBIDDEN)")
    public MeDto release(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ReleaseRequest req) {
        return service.release(me, req.dates());
    }

    @DeleteMapping("/me/releases/{date}")
    @Operation(summary = "Reprendre ma place", description = "Accès : titulaire. Seulement si personne n'a réservé la place ce jour-là.")
    @ApiResponse(responseCode = "200", description = "Ma situation")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas titulaire d'une place (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Libération introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Place déjà réservée (SPOT_ALREADY_TAKEN)")
    public MeDto takeBack(@AuthenticationPrincipal AuthUser me, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.takeBack(me, date);
    }

    @PostMapping("/days/{date}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Réserver une place", description = "Accès : authentifié. Jour ouvré, pas passé, au plus 7 jours à l'avance ; une place par personne et par jour ; un titulaire qui garde sa place ne réserve pas. `spotId` facultatif : sinon la première place libre. Réservations simultanées de la même place : une seule réussit.")
    @ApiResponse(responseCode = "201", description = "Réservation")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Place introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SPOT_ALREADY_TAKEN, ALREADY_BOOKED_TODAY, BOOKING_TOO_EARLY ou HOLDER_HAS_SPOT")
    public MyBookingDto book(@AuthenticationPrincipal AuthUser me, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @Valid @RequestBody(required = false) BookRequest req) {
        return service.book(me, date, req == null ? null : req.spotId());
    }

    @DeleteMapping("/days/{date}/bookings/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Annuler ma réservation", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "204", description = "Réservation annulée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Réservation introuvable (NOT_FOUND)")
    public void cancel(@AuthenticationPrincipal AuthUser me, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.cancel(me, date);
    }

    @GetMapping("/spots")
    @Operation(summary = "Places et titulaires", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "200", description = "Places")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<SpotDto> spots(@AuthenticationPrincipal AuthUser me) {
        return service.list(me);
    }

    @PostMapping("/spots")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une place", description = "Accès : ADMIN. Numéro unique.")
    @ApiResponse(responseCode = "201", description = "Place créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Numéro existant (SPOT_EXISTS)")
    public SpotDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SpotRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/spots/{id}/holder")
    @Operation(summary = "Attribuer une place", description = "Accès : ADMIN. `holderId` null pour retirer le titulaire ; une personne a au plus une place.")
    @ApiResponse(responseCode = "200", description = "Place")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Place ou utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà titulaire d'une autre place (HOLDER_ALREADY_HAS_SPOT)")
    public SpotDto holder(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody HolderRequest req) {
        return service.setHolder(me, id, req.holderId());
    }

    @GetMapping("/admin/usage")
    @Operation(summary = "Taux d'utilisation", description = "Accès : ADMIN. Jours ouvrés du mois (`month` AAAA-MM) où chaque place est occupée.")
    @ApiResponse(responseCode = "200", description = "Utilisation")
    @ApiResponse(responseCode = "400", description = "Mois invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<UsageDto> usage(@AuthenticationPrincipal AuthUser me,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.usage(me, month);
    }

    @GetMapping("/candidates")
    @Operation(summary = "Titulaires possibles", description = "Accès : authentifié. Utilisateurs actifs.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> candidates() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
