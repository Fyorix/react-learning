package fr.epita.platform.apps.santa.entity;

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

@Entity
@Table(name = "santa_assignments")
@Getter
@Setter
@NoArgsConstructor
public class Assignment {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "giver_id", nullable = false)
    private UUID giverId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Column(nullable = false)
    private boolean ready;

    @Column(name = "ready_at")
    private Instant readyAt;

    public Assignment(UUID eventId, UUID giverId, UUID receiverId) {
        this.eventId = eventId;
        this.giverId = giverId;
        this.receiverId = receiverId;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
