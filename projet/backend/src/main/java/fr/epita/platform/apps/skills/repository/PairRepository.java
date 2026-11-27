package fr.epita.platform.apps.skills.repository;

import fr.epita.platform.apps.skills.entity.Pair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PairRepository extends JpaRepository<Pair, UUID> {

    List<Pair> findByMentorIdOrLearnerIdOrderByCreatedAtDesc(UUID mentorId, UUID learnerId);
    long countByMentorIdAndStatus(UUID mentorId, String status);
    List<Pair> findByLearnerIdAndSkillIdAndStatus(UUID learnerId, UUID skillId, String status);
}
