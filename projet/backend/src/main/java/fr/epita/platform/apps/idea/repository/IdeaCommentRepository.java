package fr.epita.platform.apps.idea.repository;

import fr.epita.platform.apps.idea.entity.IdeaComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IdeaCommentRepository extends JpaRepository<IdeaComment, UUID> {

    List<IdeaComment> findByIdeaIdOrderByCreatedAtAsc(UUID ideaId);

    long countByIdeaId(UUID ideaId);
}
