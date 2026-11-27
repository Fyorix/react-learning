package fr.epita.platform.apps.quiz.repository;

import fr.epita.platform.apps.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    List<Quiz> findAllByOrderByCreatedAtDesc();
}
