package fr.epita.platform.apps.pomodoro.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pomodoro_members")
@Getter
@Setter
@NoArgsConstructor
public class PomodoroMember {

    @EmbeddedId
    private Key id;
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
    /** Début de la présence continue (remis à zéro après une absence de plus de 60 s). */
    @Column(name = "present_since")
    private Instant presentSince;

    public PomodoroMember(UUID roomId, UUID userId) {
        this.id = new Key(roomId, userId);
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "room_id")
        private UUID roomId;
        @Column(name = "user_id")
        private UUID userId;
    }
}
