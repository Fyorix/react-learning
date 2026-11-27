package fr.epita.platform.apps.budget.repository;

import fr.epita.platform.apps.budget.entity.FiscalYear;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalYearRepository extends JpaRepository<FiscalYear, Integer> {
}
