package fr.epita.platform.apps.attendance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Ligne d'appel : son identifiant sert d'identifiant d'absence pour les justificatifs. */
@Entity
@Table(name = "attendance_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceEntry {

    @Id
    private UUID id;
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PresenceStatus status;
}
