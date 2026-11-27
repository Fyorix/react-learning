package fr.epita.platform.apps.referral.repository;

import fr.epita.platform.apps.referral.entity.ReferralJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReferralJobRepository extends JpaRepository<ReferralJob, UUID> {

    List<ReferralJob> findAllByOrderByCreatedAtDesc();

    List<ReferralJob> findByOpenTrueOrderByCreatedAtDesc();
}
