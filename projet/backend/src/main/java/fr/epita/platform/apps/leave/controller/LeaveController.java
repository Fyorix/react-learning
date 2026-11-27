package fr.epita.platform.apps.leave.controller;

import fr.epita.platform.apps.leave.dto.LeaveDtos.AbsenceDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.BalanceRequest;
import fr.epita.platform.apps.leave.dto.LeaveDtos.BalancesDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.LeaveDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.LeaveRequestBody;
import fr.epita.platform.apps.leave.dto.LeaveDtos.PreviewDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.ProfileDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.RejectRequest;
import fr.epita.platform.apps.leave.entity.LeaveEnums.Half;
import fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveStatus;
import fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveType;
import fr.epita.platform.apps.leave.service.LeaveService;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Congés", description = "Demandes de congés, validation par le manager et soldes de jours.")
@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String DAYS = "Durée en jours ouvrés (week-ends et jours fériés français exclus), demi-journées comprises.";

    private final LeaveService service;

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demander un congé", description = "Accès : authentifié. " + DAYS + " PAID et RTT : la durée ne dépasse pas le solde restant (acquis − pris − en attente). Deux demandes non refusées ne se chevauchent pas (à la demi-journée près).")
    @ApiResponse(responseCode = "201", description = "Demande créée (PENDING)")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "409", description = "INSUFFICIENT_BALANCE ou LEAVE_OVERLAP")
    public LeaveDto request(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody LeaveRequestBody req) {
        return service.request(me, req);
    }

    @GetMapping("/requests/me")
    @Operation(summary = "Mes demandes", description = "Accès : authentifié. Filtre facultatif `year`.")
    @ApiResponse(responseCode = "200", description = "Demandes, de la plus récente à la plus ancienne")
    @ApiResponse(responseCode = "401", description = U401)
    public List<LeaveDto> mine(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Integer year) {
        return service.mine(me, year);
    }

    @DeleteMapping("/requests/{id}")
    @Operation(summary = "Annuler ma demande", description = "Accès : le salarié. Une demande PENDING s'annule à tout moment ; une demande APPROVED seulement si elle n'a pas commencé (le solde est recrédité).")
    @ApiResponse(responseCode = "200", description = "Demande annulée (CANCELLED)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas votre demande (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Demande introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "LEAVE_STARTED ou INVALID_STATUS_TRANSITION")
    public LeaveDto cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.cancel(me, id);
    }

    @GetMapping("/balances/me")
    @Operation(summary = "Mes soldes", description = "Accès : authentifié. Par type (PAID, RTT) : acquis, pris (validés), en attente, restant. `year` : année courante par défaut.")
    @ApiResponse(responseCode = "200", description = "Soldes")
    @ApiResponse(responseCode = "401", description = U401)
    public BalancesDto balances(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Integer year) {
        return service.balances(me, year);
    }

    @GetMapping("/me/profile")
    @Operation(summary = "Mon manager et mes salariés", description = "Accès : authentifié. Sert au front à afficher la page de validation (ajout au §5.14).")
    @ApiResponse(responseCode = "200", description = "Profil")
    @ApiResponse(responseCode = "401", description = U401)
    public ProfileDto profile(@AuthenticationPrincipal AuthUser me) {
        return service.profile(me);
    }

    @GetMapping("/team/requests")
    @Operation(summary = "Demandes de mes salariés", description = "Accès : manager (ADMIN : toutes). Filtre `status` (PENDING pour les demandes à valider).")
    @ApiResponse(responseCode = "200", description = "Demandes")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<LeaveDto> team(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) LeaveStatus status) {
        return service.teamRequests(me, status);
    }

    @PostMapping("/requests/{id}/approve")
    @Operation(summary = "Valider une demande", description = "Accès : manager du salarié ou ADMIN, jamais pour soi-même. Demande PENDING uniquement.")
    @ApiResponse(responseCode = "200", description = "Demande validée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le manager du salarié, ou sa propre demande (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Demande introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Demande déjà traitée (INVALID_STATUS_TRANSITION)")
    public LeaveDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.approve(me, id);
    }

    @PostMapping("/requests/{id}/reject")
    @Operation(summary = "Refuser une demande", description = "Accès : manager du salarié ou ADMIN, jamais pour soi-même. Motif obligatoire.")
    @ApiResponse(responseCode = "200", description = "Demande refusée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le manager du salarié, ou sa propre demande (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Demande introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Demande déjà traitée (INVALID_STATUS_TRANSITION)")
    public LeaveDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return service.reject(me, id, req.reason());
    }

    @GetMapping("/team/calendar")
    @Operation(summary = "Calendrier de l'équipe", description = "Accès : authentifié. Absences validées du mois (`month` AAAA-MM) : moi, mon manager, mes collègues et mes salariés. Le type n'est renvoyé qu'au manager concerné (et à soi-même).")
    @ApiResponse(responseCode = "200", description = "Absences")
    @ApiResponse(responseCode = "400", description = "Mois invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<AbsenceDto> calendar(@AuthenticationPrincipal AuthUser me,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.teamCalendar(me, month);
    }

    @GetMapping("/admin/balances")
    @Operation(summary = "Soldes de tous les salariés", description = "Accès : ADMIN. `year` : année courante par défaut.")
    @ApiResponse(responseCode = "200", description = "Soldes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    public List<BalancesDto> allBalances(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Integer year) {
        return service.allBalances(me, year);
    }

    @PutMapping("/admin/balances/{userId}")
    @Operation(summary = "Attribuer les soldes annuels", description = "Accès : ADMIN. Valeurs par demi-journée.")
    @ApiResponse(responseCode = "200", description = "Soldes enregistrés")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    public BalancesDto setBalance(@AuthenticationPrincipal AuthUser me, @PathVariable UUID userId,
                                  @Valid @RequestBody BalanceRequest req) {
        return service.setBalance(me, userId, req);
    }

    @GetMapping("/preview")
    @Operation(summary = "Aperçu d'une demande", description = "Accès : authentifié. Nombre de jours décomptés et solde après demande, sans rien enregistrer. " + DAYS)
    @ApiResponse(responseCode = "200", description = "Aperçu")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public PreviewDto preview(@AuthenticationPrincipal AuthUser me, @RequestParam LeaveType type,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                              @RequestParam(required = false) Half startHalf, @RequestParam(required = false) Half endHalf) {
        return service.preview(me, new LeaveRequestBody(type, startDate, endDate, startHalf, endHalf, null));
    }
}
