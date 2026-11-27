package fr.epita.platform.apps.crm.repository;

import fr.epita.platform.apps.crm.entity.Opportunity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

    List<Opportunity> findByAccountIdOrderByExpectedCloseDateAsc(UUID accountId);

    List<Opportunity> findAllByOrderByExpectedCloseDateAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Opportunity o where o.id = :id")
    Optional<Opportunity> lock(@Param("id") UUID id);
}
