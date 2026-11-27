package fr.epita.platform.apps.skills.repository;

import fr.epita.platform.apps.skills.entity.MentoringRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MentoringRequestRepository extends JpaRepository<MentoringRequest, UUID> {

    List<MentoringRequest> findByLearnerIdOrderByCreatedAtDesc(UUID learnerId);
    List<MentoringRequest> findByMentorIdOrderByCreatedAtDesc(UUID mentorId);
}
