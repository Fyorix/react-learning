package fr.epita.platform.apps.quiz.repository;

import fr.epita.platform.apps.quiz.entity.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    boolean existsByQuizId(UUID quizId);

    boolean existsByQuizIdAndUserId(UUID quizId, UUID userId);

    long countByQuizId(UUID quizId);

    List<Attempt> findByQuizIdOrderByScoreDescSubmittedAtAsc(UUID quizId);

    List<Attempt> findByUserIdOrderBySubmittedAtDesc(UUID userId);

    List<Attempt> findAll();
}
