package fr.epita.platform.apps.budget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "budget_years")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FiscalYear {

    @Id
    private Integer year;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private YearStatus status;
}
