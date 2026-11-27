package fr.epita.platform.apps.budget.entity;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "budget_requests")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseRequest extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;
    @Column(nullable = false)
    private int year;
    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BudgetCategory category;
    @Column(nullable = false)
    private String label;
    @Column(name = "estimated_amount", nullable = false)
    private BigDecimal estimatedAmount;
    private String supplier;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseStatus status;
    @Column(name = "spent_amount")
    private BigDecimal spentAmount;
    @Column(name = "spent_on")
    private LocalDate spentOn;
    @Column(name = "decided_by")
    private UUID decidedBy;
    private String reason;
    @Column(name = "override_reason")
    private String overrideReason;
}
