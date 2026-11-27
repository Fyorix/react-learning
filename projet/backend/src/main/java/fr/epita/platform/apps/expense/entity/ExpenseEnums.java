package fr.epita.platform.apps.expense.entity;

import java.math.BigDecimal;

public final class ExpenseEnums {

    private ExpenseEnums() {
    }

    /** Catégories et plafond par dépense (null : pas de plafond). */
    public enum Category {
        MEAL(new BigDecimal("25.00")), HOTEL(new BigDecimal("120.00")), TRANSPORT(null), OTHER(null);

        private final BigDecimal cap;

        Category(BigDecimal cap) {
            this.cap = cap;
        }

        public BigDecimal cap() {
            return cap;
        }
    }

    public enum ClaimStatus { DRAFT, SUBMITTED, VALIDATED, REJECTED, REIMBURSED }
}
