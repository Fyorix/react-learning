package fr.epita.platform.apps.santa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** « giver ne doit pas offrir à receiver ». */
@Entity
@Table(name = "santa_exclusions")
@Getter
@Setter
@NoArgsConstructor
public class Exclusion {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "giver_id", nullable = false)
    private UUID giverId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    public Exclusion(UUID eventId, UUID giverId, UUID receiverId) {
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
