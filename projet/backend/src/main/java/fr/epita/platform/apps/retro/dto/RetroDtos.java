package fr.epita.platform.apps.retro.dto;

import fr.epita.platform.apps.retro.entity.CardColumn;
import fr.epita.platform.apps.retro.entity.RetroPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RetroDtos {

    private RetroDtos() {
    }

    @Schema(description = "Nouvelle équipe (le créateur en fait partie)")
    public record TeamRequest(
            @Schema(description = "Nom, 80 caractères maximum", example = "Squad Paiement")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Membres (le créateur est ajouté d'office)")
            @NotNull @Size(max = 50) List<UUID> memberIds) {
    }

    @Schema(description = "Nouvelle rétrospective (le créateur anime)")
    public record RetroRequest(
            @Schema(description = "Équipe dont je suis membre", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID teamId,
            @Schema(description = "Titre, 120 caractères maximum", example = "Rétro de fin de sprint")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Sprint, 40 caractères maximum", example = "Sprint 42")
            @NotBlank @Size(max = 40) String sprint) {
    }

    @Schema(description = "Commande du timer : START (durée), PAUSE, RESUME, ADD (secondes ajoutées)")
    public record TimerRequest(
            @Schema(description = "START, PAUSE, RESUME ou ADD", example = "START")
            @NotNull TimerAction action,
            @Schema(description = "Secondes (START, ADD), de 10 à 3 600", example = "300")
            @Min(10) @Max(3600) Integer seconds) {
    }

    public enum TimerAction { START, PAUSE, RESUME, ADD }

    @Schema(description = "Carte")
    public record CardRequest(
            @Schema(description = "WENT_WELL ou TO_IMPROVE", example = "TO_IMPROVE")
            @NotNull CardColumn column,
            @Schema(description = "Texte, 500 caractères maximum", example = "Trop de réunions le lundi")
            @NotBlank @Size(max = 500) String text) {
    }

    @Schema(description = "Regroupement de cartes sous un titre")
    public record GroupRequest(
            @Schema(description = "Titre, 120 caractères maximum", example = "Organisation des réunions")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Cartes de la rétrospective (au moins une)")
            @NotEmpty @Size(max = 50) List<UUID> cardIds) {
    }

    @Schema(description = "Un vote sur une carte ou un groupe")
    public record VoteItem(
            @Schema(description = "Carte ou groupe", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID targetId,
            @Schema(description = "1 ou 2 votes", example = "2")
            @Min(1) @Max(2) int count) {
    }

    @Schema(description = "Mes votes (remplacent les précédents) : 3 au total, 2 maximum par cible")
    public record VotesRequest(
            @Schema(description = "Votes")
            @NotNull @Size(max = 3) List<@Valid VoteItem> votes) {
    }

    @Schema(description = "Action décidée")
    public record ActionRequest(
            @Schema(description = "Texte, 300 caractères maximum", example = "Déplacer le point d'équipe au mardi")
            @NotBlank @Size(max = 300) String text,
            @Schema(description = "Responsable, membre de l'équipe", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID ownerId,
            @Schema(description = "Échéance", example = "2026-12-15")
            @NotNull LocalDate dueDate) {
    }

    @Schema(description = "Avancement d'une action")
    public record DoneRequest(
            @Schema(description = "Action terminée", example = "true")
            @NotNull Boolean done) {
    }

    @Schema(description = "Équipe")
    public record TeamDto(UUID id, String name, List<MemberDto> members) {
    }

    @Schema(description = "Membre")
    public record MemberDto(UUID userId, String name) {
    }

    @Schema(description = "Rétrospective dans une liste")
    public record RetroSummary(UUID id, UUID teamId, String teamName, String title, String sprint, String facilitatorName, RetroPhase phase,
                               long actions, Instant createdAt) {
    }

    @Schema(description = "Carte visible ; `votes` null tant que le vote n'est pas terminé")
    public record CardDto(UUID id, CardColumn column, String text, UUID authorId, String authorName, boolean mine, UUID groupId, Integer votes) {
    }

    @Schema(description = "Groupe de cartes ; `votes` null tant que le vote n'est pas terminé")
    public record GroupDto(UUID id, String title, List<UUID> cardIds, Integer votes) {
    }

    @Schema(description = "Action")
    public record ActionDto(UUID id, UUID retroId, String text, UUID ownerId, String ownerName, LocalDate dueDate, boolean done) {
    }

    @Schema(description = """
            État complet. `timerEndsAt` (décompte en cours) ou `timerRemaining` (en pause, secondes) ; `serverTime` sert au front \
            à corriger le décalage d'horloge. En WRITING, `cards` ne contient que mes cartes (`hiddenCards` = celles des autres).""")
    public record RetroDto(UUID id, UUID teamId, String teamName, String title, String sprint, UUID facilitatorId, String facilitatorName,
                           boolean facilitator, RetroPhase phase, Instant timerEndsAt, Integer timerRemaining, Instant serverTime,
                           List<CardDto> cards, int hiddenCards, List<GroupDto> groups, List<VoteItem> myVotes, int votesLeft,
                           List<ActionDto> actions, List<ActionDto> previousOpenActions, List<MemberDto> members) {
    }
}
