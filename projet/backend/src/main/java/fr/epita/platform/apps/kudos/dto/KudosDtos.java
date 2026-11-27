package fr.epita.platform.apps.kudos.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KudosDtos {

    private KudosDtos() {
    }

    @Schema(description = "Nouveau kudo")
    public record KudoRequest(
            @Schema(description = "Destinataires : 1 à 5 collègues, jamais soi-même", example = "[\"9b76d11e-ec02-474c-8ca9-8886c4bfbb7d\"]")
            @NotNull @Size(min = 1, max = 5) List<UUID> recipientIds,
            @Schema(description = "Message de remerciement, 10 à 500 caractères", example = "Merci pour ton aide sur la mise en production !")
            @NotBlank @Size(min = 10, max = 500) String message,
            @Schema(description = "Valeur de l'entreprise associée (voir GET /api/kudos/values)", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID valueId) {
    }

    @Schema(description = "Ajout d'une valeur (ADMIN)")
    public record ValueRequest(
            @Schema(description = "Nom unique, 60 caractères maximum", example = "Entraide")
            @NotBlank @Size(max = 60) String name) {
    }

    @Schema(description = "Personne")
    public record PersonDto(UUID id, String displayName) {
    }

    @Schema(description = "Réaction : emoji, nombre et si j'ai réagi")
    public record ReactionDto(String emoji, long count, boolean mine) {
    }

    @Schema(description = "Kudo")
    public record KudoDto(UUID id, PersonDto author, List<PersonDto> recipients, String message, UUID valueId,
                          String valueName, List<ReactionDto> reactions, boolean canDelete, Instant createdAt) {
    }

    @Schema(description = "Page du mur. `nextCursor` est à repasser dans `cursor` pour la page suivante ; null en fin de liste.")
    public record WallDto(List<KudoDto> items, UUID nextCursor) {
    }

    @Schema(description = "Valeur de l'entreprise")
    public record ValueDto(UUID id, String name) {
    }

    @Schema(description = "Profil kudos d'un collègue")
    public record ProfileDto(PersonDto user, long received, long sent, Map<String, Long> receivedByValue, List<KudoDto> latestReceived) {
    }

    @Schema(description = "Ligne du classement")
    public record RankDto(int rank, PersonDto user, long kudos) {
    }
}
