package fr.epita.platform.apps.expense.repository;

import fr.epita.platform.apps.expense.entity.ExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, UUID> {
}
