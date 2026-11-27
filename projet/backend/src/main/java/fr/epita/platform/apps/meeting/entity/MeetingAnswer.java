package fr.epita.platform.apps.meeting.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "meeting_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MeetingAnswer {

    @EmbeddedId
    private Key id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Availability answer;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "slot_id")
        private UUID slotId;
        @Column(name = "user_id")
        private UUID userId;
    }
}
