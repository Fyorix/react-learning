package fr.epita.platform.apps.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "goals")
@Getter
@Setter
@NoArgsConstructor
public class Goal {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String metric;

    @Column(nullable = false)
    private String unit;

    @Column(name = "start_value", nullable = false)
    private BigDecimal startValue;

    @Column(name = "target_value", nullable = false)
    private BigDecimal targetValue;

    @Column(name = "current_value")
    private BigDecimal currentValue;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalStatus status = GoalStatus.IN_PROGRESS;

    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Progression de startValue à targetValue, bornée à [0, 100]. */
    public int progressPercent() {
        BigDecimal current = currentValue != null ? currentValue : startValue;
        BigDecimal range = targetValue.subtract(startValue);
        if (range.signum() == 0) {
            return 0;
        }
        int pct = current.subtract(startValue).multiply(BigDecimal.valueOf(100))
                .divide(range, 0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, pct));
    }
}
