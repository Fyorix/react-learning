package fr.epita.platform.apps.appraisal.repository;

import fr.epita.platform.apps.appraisal.entity.Appraisal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppraisalRepository extends JpaRepository<Appraisal, UUID> {

    List<Appraisal> findByCampaignId(UUID campaignId);

    List<Appraisal> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<Appraisal> findByManagerIdOrderByCreatedAtDesc(UUID managerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appraisal a where a.id = :id")
    Optional<Appraisal> lock(@Param("id") UUID id);
}
