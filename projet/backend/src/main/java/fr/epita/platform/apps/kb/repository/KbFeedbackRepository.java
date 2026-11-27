package fr.epita.platform.apps.kb.repository;

import fr.epita.platform.apps.kb.entity.KbFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KbFeedbackRepository extends JpaRepository<KbFeedback, KbFeedback.Key> {

    List<KbFeedback> findByIdArticleId(UUID articleId);
}
