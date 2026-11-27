package fr.epita.platform.apps.meeting.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "meeting_polls")
@Getter
@Setter
@NoArgsConstructor
public class MeetingPoll extends BaseEntity {

    @Column(nullable = false)
    private String title;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    private String location;
    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PollStatus status;
    @Column(name = "decided_slot_id")
    private UUID decidedSlotId;
    @Column(name = "last_reminder_at")
    private Instant lastReminderAt;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "meeting_poll_participants", joinColumns = @JoinColumn(name = "poll_id"))
    @Column(name = "user_id")
    private Set<UUID> participantIds = new HashSet<>();
}
