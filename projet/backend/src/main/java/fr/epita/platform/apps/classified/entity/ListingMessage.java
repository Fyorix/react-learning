package fr.epita.platform.apps.classified.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "classified_messages")
@Getter
@Setter
@NoArgsConstructor
public class ListingMessage extends BaseEntity {

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;
    @Column(name = "sender_id", nullable = false)
    private UUID senderId;
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;
    @Column(nullable = false)
    private String content;
}
