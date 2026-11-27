package fr.epita.platform.apps.timesheet.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Feuille de temps d'un salarié pour une semaine ISO. */
@Entity
@Table(name = "timesheet_weeks")
@Getter
@Setter
@NoArgsConstructor
public class Timesheet extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private String week;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WeekStatus status = WeekStatus.DRAFT;
    private String comment;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @OneToMany(mappedBy = "timesheet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimeEntry> entries = new ArrayList<>();
}
