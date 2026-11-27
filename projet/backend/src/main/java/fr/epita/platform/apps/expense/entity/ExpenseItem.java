package fr.epita.platform.apps.expense.entity;

import fr.epita.platform.apps.expense.entity.ExpenseEnums.Category;
import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expense_items")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private ExpenseClaim claim;
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String description;
    private String justification;
    @Column(name = "receipt_key", nullable = false)
    private String receiptKey;
    @Column(name = "receipt_type", nullable = false)
    private String receiptType;
    @Column(name = "receipt_name")
    private String receiptName;
    private Boolean accepted;
    @Column(name = "reject_reason")
    private String rejectReason;
}
