package fr.epita.platform.apps.tournament.dto;

import fr.epita.platform.apps.tournament.entity.Format;
import fr.epita.platform.apps.tournament.entity.MatchStatus;
import fr.epita.platform.apps.tournament.entity.Mode;
import fr.epita.platform.apps.tournament.entity.TournamentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TournamentDtos {

    private TournamentDtos() {
    }

    @Schema(description = "Nouveau tournoi")
    public record TournamentRequest(
            @Schema(description = "Nom, 100 caractères maximum", example = "Coupe de Noël")
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "SINGLE_ELIMINATION (élimination directe) ou ROUND_ROBIN (poule unique)", example = "SINGLE_ELIMINATION")
            @NotNull Format format,
            @Schema(description = "SINGLES (1 contre 1) ou DOUBLES (2 contre 2)", example = "DOUBLES")
            @NotNull Mode mode,
            @Schema(description = "Fin des inscriptions (ISO-8601, dans le futur)", example = "2026-12-15T12:00:00Z")
            @NotNull Instant registrationDeadline) {
    }

    @Schema(description = "Inscription")
    public record RegistrationRequest(
            @Schema(description = "Coéquipier (obligatoire en double, interdit en simple)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            UUID partnerId) {
    }

    @Schema(description = "Score en 10 points gagnants : l'un des deux vaut 10, l'autre de 0 à 9")
    public record ScoreRequest(
            @Schema(description = "Points du camp A", example = "10")
            @NotNull @Min(0) @Max(10) Integer scoreA,
            @Schema(description = "Points du camp B", example = "7")
            @NotNull @Min(0) @Max(10) Integer scoreB) {
    }

    @Schema(description = "Tournoi dans une liste")
    public record TournamentSummary(UUID id, String name, Format format, Mode mode, TournamentStatus status,
                                    Instant registrationDeadline, String organizerName, long entries, boolean registered) {
    }

    @Schema(description = "Participant (joueur ou équipe) ; `elo` = moyenne de l'équipe en double")
    public record EntryDto(UUID id, String name, List<UUID> playerIds, int elo, Integer seed) {
    }

    @Schema(description = """
            Match. `round` 1 = premier tour (élimination) ; `entryA`/`entryB` null = adversaire pas encore connu ou exempt. \
            `canReport` / `canConfirm` : actions possibles pour l'utilisateur courant.""")
    public record MatchDto(UUID id, int round, int position, EntryDto entryA, EntryDto entryB, Integer scoreA, Integer scoreB,
                           MatchStatus status, UUID reportedBy, String reportedByName, UUID winnerId,
                           boolean canReport, boolean canConfirm, String tournamentName) {
    }

    @Schema(description = "Ligne du classement d'une poule")
    public record StandingDto(UUID entryId, String name, int played, int wins, int losses, int pointsFor, int pointsAgainst) {
    }

    @Schema(description = "Détail d'un tournoi : participants, matchs, classement de poule, vainqueur")
    public record TournamentDto(UUID id, String name, Format format, Mode mode, TournamentStatus status, Instant registrationDeadline,
                                UUID organizerId, String organizerName, boolean organizer, boolean registered, int rounds,
                                List<EntryDto> entries, List<MatchDto> matches, List<StandingDto> standings, EntryDto champion) {
    }

    @Schema(description = "Ligne du classement Elo")
    public record RankingRow(int rank, UUID userId, String name, int elo, int played, int wins) {
    }

    @Schema(description = "Profil joueur : Elo et historique des matchs confirmés")
    public record PlayerDto(UUID userId, String name, int elo, int played, int wins, List<MatchDto> history) {
    }
}
