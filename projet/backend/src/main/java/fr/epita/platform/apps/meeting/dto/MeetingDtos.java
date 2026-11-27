package fr.epita.platform.apps.meeting.dto;

import fr.epita.platform.apps.meeting.entity.Availability;
import fr.epita.platform.apps.meeting.entity.PollStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MeetingDtos {

    private MeetingDtos() {
    }

    @Schema(description = "Proposition de réunion : 2 à 20 créneaux futurs, sans doublon")
    public record PollRequest(
            @Schema(description = "Titre, 120 caractères maximum", example = "Point d'avancement projet")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Durée en minutes, de 15 à 480", example = "60")
            @Min(15) @Max(480) int durationMinutes,
            @Schema(description = "Lieu ou lien de visio (facultatif), 120 caractères maximum", example = "Salle Turing")
            @Size(max = 120) String location,
            @Schema(description = "Participants invités (l'organisateur est ajouté d'office)")
            @NotEmpty @Size(max = 50) List<UUID> participantIds,
            @Schema(description = "Créneaux proposés")
            @NotNull @Size(min = 2, max = 20) List<@Valid SlotInput> slots) {
    }

    @Schema(description = "Créneau proposé")
    public record SlotInput(
            @Schema(description = "Début (ISO-8601), dans le futur", example = "2026-12-03T09:00:00Z")
            @NotNull Instant start) {
    }

    @Schema(description = "Mes réponses (remplacent les précédentes)")
    public record AnswersRequest(
            @Schema(description = "Une réponse par créneau")
            @NotNull @Size(max = 20) List<@Valid AnswerInput> answers) {
    }

    @Schema(description = "Réponse à un créneau")
    public record AnswerInput(
            @Schema(description = "Créneau de la proposition", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID slotId,
            @Schema(description = "Disponibilité : YES, IF_NEEDED ou NO", example = "YES")
            @NotNull Availability answer) {
    }

    @Schema(description = "Créneau retenu")
    public record DecideRequest(
            @Schema(description = "Créneau de la proposition, non passé", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID slotId) {
    }

    @Schema(description = "Proposition (liste)")
    public record PollSummary(UUID id, String title, int durationMinutes, String organizerName, PollStatus status, boolean organizer,
                              boolean answeredByMe, int participants, int respondents, Instant decidedStart, Instant createdAt) {
    }

    @Schema(description = "Créneau avec le décompte des réponses")
    public record SlotDto(UUID id, Instant start, Instant end, int yes, int ifNeeded, int no, boolean suggested, boolean decided, boolean past) {
    }

    @Schema(description = "Participant et ses réponses (`answers` : slotId → réponse)")
    public record ParticipantDto(UUID userId, String name, boolean organizer, boolean answered, java.util.Map<UUID, Availability> answers) {
    }

    @Schema(description = "Détail : créneaux, réponses de chacun, créneau suggéré")
    public record PollDto(UUID id, String title, int durationMinutes, String location, UUID organizerId, String organizerName, PollStatus status,
                          boolean organizer, List<SlotDto> slots, List<ParticipantDto> participants, UUID suggestedSlotId, UUID decidedSlotId,
                          Instant lastReminderAt) {
    }

    @Schema(description = "Relance : participants relancés")
    public record ReminderDto(List<String> reminded, Instant at) {
    }
}
