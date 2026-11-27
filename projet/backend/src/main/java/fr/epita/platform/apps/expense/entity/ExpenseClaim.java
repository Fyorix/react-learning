package fr.epita.platform.apps.expense.entity;

import fr.epita.platform.apps.expense.entity.ExpenseEnums.ClaimStatus;
import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Note de frais mensuelle d'un salarié. */
@Entity
@Table(name = "expense_claims")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseClaim extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private String month;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.DRAFT;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "reimbursed_at")
    private Instant reimbursedAt;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("expenseDate ASC")
    private List<ExpenseItem> items = new ArrayList<>();
}
