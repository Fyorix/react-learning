package fr.epita.platform.apps.referral.repository;

import fr.epita.platform.apps.referral.entity.Referral;
import fr.epita.platform.apps.referral.entity.ReferralStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    List<Referral> findByReferrerIdOrderByCreatedAtDesc(UUID referrerId);

    List<Referral> findAllByOrderByCreatedAtDesc();

    List<Referral> findByStatusOrderByStatusChangedAtAsc(ReferralStatus status);

    boolean existsByJobIdAndCandidateEmail(UUID jobId, String candidateEmail);

    long countByJobId(UUID jobId);

    /** Expiration : sans évolution depuis la date limite, une recommandation en cours passe EXPIRED. */
    @Modifying
    @Query("update Referral r set r.status = fr.epita.platform.apps.referral.entity.ReferralStatus.EXPIRED, r.statusChangedAt = :now "
            + "where r.status in :active and r.statusChangedAt < :limit")
    int expire(@Param("active") Collection<ReferralStatus> active, @Param("limit") Instant limit, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Referral r where r.id = :id")
    Optional<Referral> lock(@Param("id") UUID id);
}
