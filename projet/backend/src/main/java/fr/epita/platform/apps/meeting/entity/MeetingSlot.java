package fr.epita.platform.apps.meeting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meeting_slots")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MeetingSlot {

    @Id
    private UUID id;
    @Column(name = "poll_id", nullable = false)
    private UUID pollId;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
}
