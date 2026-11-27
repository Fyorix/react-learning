package fr.epita.platform.apps.fitness.controller;

import fr.epita.platform.apps.fitness.dto.FitnessDtos.*;
import fr.epita.platform.apps.fitness.service.FitnessService;
import fr.epita.platform.common.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Cours de sport", description = "Cours collectifs réservables avec 8 crédits par mois, liste d'attente et annulations.")
@RestController
@RequestMapping("/api/fitness")
@RequiredArgsConstructor
public class FitnessController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String C404 = "Cours introuvable (NOT_FOUND)";

    private final FitnessService service;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. COACH le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/classes")
    @Operation(summary = "Planning des cours", description = "Accès : authentifié. Par défaut les 7 prochains jours ; filtre par type.")
    @ApiResponse(responseCode = "200", description = "Cours")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public List<ClassSummary> classes(@AuthenticationPrincipal AuthUser me,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                      @RequestParam(required = false) String type) {
        return service.planning(me, from, to, type);
    }

    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un cours", description = "Accès : COACH (ses propres cours) ou ADMIN (pour un coach).")
    @ApiResponse(responseCode = "201", description = "Cours créé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux COACH (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Coach introuvable (NOT_FOUND)")
    public ClassSummary create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ClassRequest req) {
        return service.create(me, req);
    }

    @GetMapping("/classes/{id}")
    @Operation(summary = "Détail d'un cours", description = "Accès : authentifié. Places, mon statut et mon rang ; liste des inscrits pour le coach et les ADMIN.")
    @ApiResponse(responseCode = "200", description = "Cours")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = C404)
    public ClassDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/classes/{id}/cancel")
    @Operation(summary = "Annuler le cours", description = "Accès : coach du cours ou ADMIN. Les crédits sont rendus à tous les inscrits.")
    @ApiResponse(responseCode = "200", description = "Cours annulé")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Ni coach du cours ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = C404)
    @ApiResponse(responseCode = "409", description = "CLASS_CANCELLED ou CLASS_STARTED")
    public ClassSummary cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.cancelClass(me, id);
    }

    @PostMapping("/classes/{id}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Réserver", description = "Accès : authentifié. 1 crédit débité si CONFIRMED ; cours complet : WAITLISTED sans débit (avec rang). 2 cours par jour maximum, sans chevauchement.")
    @ApiResponse(responseCode = "201", description = "Réservation")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = C404)
    @ApiResponse(responseCode = "409", description = "NO_CREDIT_LEFT, DAILY_LIMIT_REACHED, OVERLAPPING_CLASS, ALREADY_BOOKED, CLASS_STARTED ou CLASS_CANCELLED")
    public BookingResult book(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.book(me, id);
    }

    @DeleteMapping("/classes/{id}/bookings/me")
    @Operation(summary = "Annuler ma réservation", description = "Accès : inscrit. Gratuite jusqu'à 2 h avant (crédit rendu), sinon crédit perdu ; la place promeut le premier en attente ayant un crédit.")
    @ApiResponse(responseCode = "200", description = "Cours")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Cours ou réservation introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Cours commencé (CLASS_STARTED)")
    public ClassDto cancelBooking(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.cancelBooking(me, id);
    }

    @GetMapping("/me")
    @Operation(summary = "Mes crédits et réservations", description = "Accès : authentifié. Crédits du mois en cours (8, non reportés) et réservations depuis le début du mois.")
    @ApiResponse(responseCode = "200", description = "Crédits et réservations")
    @ApiResponse(responseCode = "401", description = U401)
    public MeDto me(@AuthenticationPrincipal AuthUser me) {
        return service.me(me);
    }

    @GetMapping("/coach/classes")
    @Operation(summary = "Mes cours (coach)", description = "Accès : COACH. Cours des 30 derniers jours et à venir.")
    @ApiResponse(responseCode = "200", description = "Cours")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux COACH (FORBIDDEN)")
    public List<ClassSummary> coachClasses(@AuthenticationPrincipal AuthUser me) {
        return service.coachClasses(me);
    }

    @GetMapping("/admin/stats")
    @Operation(summary = "Statistiques de remplissage", description = "Accès : ADMIN. Par type et par coach sur le mois (`month` AAAA-MM).")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "400", description = "Mois invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    public StatsDto stats(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.stats(me, month);
    }
}
