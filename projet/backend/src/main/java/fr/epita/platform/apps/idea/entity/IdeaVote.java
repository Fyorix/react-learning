package fr.epita.platform.apps.idea.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idea_votes")
@Getter
@NoArgsConstructor
public class IdeaVote {

    @EmbeddedId
    private Key id;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    public IdeaVote(UUID ideaId, UUID userId, Instant votedAt) {
        this.id = new Key(ideaId, userId);
        this.votedAt = votedAt;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "idea_id")
        private UUID ideaId;
        @Column(name = "user_id")
        private UUID userId;
    }
}
