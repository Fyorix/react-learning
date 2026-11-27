package fr.epita.platform.apps.recruitment.repository;

import fr.epita.platform.apps.recruitment.entity.Application;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findByJobIdOrderByStageSinceAsc(UUID jobId);

    boolean existsByJobIdAndEmailIgnoreCase(UUID jobId, String email);

    long countByJobId(UUID jobId);

    @Query("select a from Application a join a.interviewerIds i where i = :u")
    List<Application> assignedTo(@Param("u") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id")
    Optional<Application> lock(@Param("id") UUID id);
}
