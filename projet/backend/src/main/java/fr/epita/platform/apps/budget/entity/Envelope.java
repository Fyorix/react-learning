package fr.epita.platform.apps.budget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "budget_envelopes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Envelope {

    @EmbeddedId
    private Key id;
    @Column(nullable = false)
    private BigDecimal amount;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "team_id")
        private UUID teamId;
        @Column(name = "year")
        private Integer year;
        @Enumerated(EnumType.STRING)
        @Column(name = "category")
        private BudgetCategory category;
    }
}
