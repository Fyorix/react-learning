package fr.epita.platform.apps.poll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Une ligne par option choisie ; le vote d'un utilisateur = l'ensemble de ses lignes pour le sondage. */
@Entity
@Table(name = "poll_votes")
@Getter
@Setter
@NoArgsConstructor
public class PollVote {

    @Id
    private UUID id;

    @Column(name = "poll_id", nullable = false)
    private UUID pollId;

    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    public PollVote(UUID pollId, UUID optionId, UUID userId, Instant votedAt) {
        this.pollId = pollId;
        this.optionId = optionId;
        this.userId = userId;
        this.votedAt = votedAt;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
