package fr.epita.platform.apps.budget.repository;

import fr.epita.platform.apps.budget.entity.BudgetTeam;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetTeamRepository extends JpaRepository<BudgetTeam, UUID> {

    List<BudgetTeam> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    /** Verrou de l'équipe : sérialise les validations (le restant ne peut pas être dépassé par deux validations simultanées). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BudgetTeam t where t.id = :id")
    Optional<BudgetTeam> lock(@Param("id") UUID id);
}
