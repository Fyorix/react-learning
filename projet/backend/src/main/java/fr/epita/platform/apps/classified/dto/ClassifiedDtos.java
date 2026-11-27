package fr.epita.platform.apps.classified.dto;

import fr.epita.platform.apps.classified.entity.ClassifiedEnums.ListingStatus;
import fr.epita.platform.apps.classified.entity.ClassifiedEnums.ListingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ClassifiedDtos {

    private ClassifiedDtos() {
    }

    @Schema(description = "Annonce (partie `data` du multipart à la création, corps JSON à la modification)")
    public record ListingRequest(
            @Schema(description = "SALE = vente (prix obligatoire), GIFT = don (sans prix), SERVICE = service rendu (sans prix)", example = "SALE")
            @NotNull ListingType type,
            @Schema(description = "Titre, 120 caractères maximum", example = "Vélo de ville, très bon état")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Description, 3000 caractères maximum", example = "Taille M, révisé en septembre, antivol fourni.")
            @NotBlank @Size(max = 3000) String description,
            @Schema(description = "Catégorie libre, 40 caractères maximum", example = "Sport")
            @NotBlank @Size(max = 40) String category,
            @Schema(description = "Prix en euros : obligatoire et > 0 pour SALE, interdit pour GIFT et SERVICE ; 2 décimales maximum", example = "120.00")
            @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer = 6, fraction = 2) BigDecimal price) {
    }

    @Schema(description = "Changement de statut par l'auteur")
    public record StatusRequest(
            @Schema(description = "ACTIVE = disponible, RESERVED = réservée, CLOSED = vendue ou donnée (définitif)", example = "RESERVED")
            @NotNull ListingStatus status) {
    }

    @Schema(description = "Message à propos d'une annonce")
    public record MessageRequest(
            @Schema(description = "Texte, 2000 caractères maximum", example = "Bonjour, le vélo est-il toujours disponible ?")
            @NotBlank @Size(max = 2000) String content,
            @Schema(description = "Destinataire : uniquement quand l'auteur répond à un interlocuteur ; sinon le message va à l'auteur", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            UUID toUserId) {
    }

    @Schema(description = "Signalement d'une annonce")
    public record ReportRequest(
            @Schema(description = "Motif, 500 caractères maximum", example = "Objet interdit")
            @NotBlank @Size(max = 500) String reason) {
    }

    @Schema(description = "Photo d'une annonce ; `url` = GET /api/classifieds/{id}/photos/{photoId}")
    public record PhotoDto(UUID id, int position, String url) {
    }

    @Schema(description = "Annonce dans une liste")
    public record ListingSummaryDto(UUID id, ListingType type, String title, String category, BigDecimal price,
                                    ListingStatus status, String authorName, PhotoDto cover, Instant createdAt) {
    }

    @Schema(description = "Annonce")
    public record ListingDto(UUID id, ListingType type, String title, String description, String category, BigDecimal price,
                             ListingStatus status, boolean hidden, UUID authorId, String authorName, List<PhotoDto> photos,
                             Instant expiresAt, boolean mine, Instant createdAt) {
    }

    @Schema(description = "Message")
    public record MessageDto(UUID id, UUID senderId, String senderName, String content, Instant createdAt) {
    }

    @Schema(description = "Conversation : une annonce et un interlocuteur")
    public record ConversationDto(UUID listingId, String listingTitle, UUID otherUserId, String otherUserName,
                                  boolean iAmAuthor, List<MessageDto> messages) {
    }

    @Schema(description = "Annonce signalée")
    public record ReportedDto(ListingSummaryDto listing, boolean hidden, List<String> reasons) {
    }
}
