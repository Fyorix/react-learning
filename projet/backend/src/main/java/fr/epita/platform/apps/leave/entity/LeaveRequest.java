package fr.epita.platform.apps.leave.entity;

import fr.epita.platform.apps.leave.entity.LeaveEnums.Half;
import fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveStatus;
import fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveType;
import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
@Getter
@Setter
@NoArgsConstructor
public class LeaveRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType type;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "start_half", nullable = false)
    private Half startHalf;
    @Enumerated(EnumType.STRING)
    @Column(name = "end_half", nullable = false)
    private Half endHalf;
    @Column(nullable = false)
    private BigDecimal days;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status = LeaveStatus.PENDING;
    private String comment;
    private String reason;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Indices de demi-journées (date × 2 + 0 pour le matin, + 1 pour l'après-midi), bornes incluses. */
    public long firstSlot() {
        return startDate.toEpochDay() * 2 + (startHalf == Half.AFTERNOON ? 1 : 0);
    }

    public long lastSlot() {
        return endDate.toEpochDay() * 2 + (endHalf == Half.MORNING ? 0 : 1);
    }

    public boolean isActive() {
        return status == LeaveStatus.PENDING || status == LeaveStatus.APPROVED;
    }
}
