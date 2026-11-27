package fr.epita.platform.apps.timesheet.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "timesheet_entries")
@Getter
@Setter
@NoArgsConstructor
public class TimeEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "week_id", nullable = false)
    private Timesheet timesheet;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "entry_date", nullable = false)
    private LocalDate date;
    @Column(nullable = false)
    private BigDecimal hours;
}
