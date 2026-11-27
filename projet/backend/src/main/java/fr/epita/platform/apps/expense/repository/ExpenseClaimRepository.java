package fr.epita.platform.apps.expense.repository;

import fr.epita.platform.apps.expense.entity.ExpenseClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> {

    Optional<ExpenseClaim> findByUserIdAndMonth(UUID userId, String month);

    List<ExpenseClaim> findByUserIdOrderByMonthDesc(UUID userId);

    List<ExpenseClaim> findByUserIdInOrderByMonthDesc(Collection<UUID> userIds);

    List<ExpenseClaim> findAllByOrderByMonthDesc();
}
