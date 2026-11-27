package fr.epita.platform.apps.attendance.entity;

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
@Table(name = "attendance_justifications")
@Getter
@Setter
@NoArgsConstructor
public class Justification extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Column(nullable = false)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JustificationStatus status;
    @Column(name = "file_key", nullable = false)
    private String fileKey;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "file_name")
    private String fileName;
    @Column(name = "admin_reason")
    private String adminReason;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "attendance_justification_entries", joinColumns = @JoinColumn(name = "justification_id"))
    @Column(name = "entry_id")
    private Set<UUID> entryIds = new HashSet<>();
}
