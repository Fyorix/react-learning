package fr.epita.platform.apps.appraisal.repository;

import fr.epita.platform.apps.appraisal.entity.AppraisalCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppraisalCampaignRepository extends JpaRepository<AppraisalCampaign, UUID> {

    boolean existsByYear(int year);

    List<AppraisalCampaign> findAllByOrderByYearDesc();
}
