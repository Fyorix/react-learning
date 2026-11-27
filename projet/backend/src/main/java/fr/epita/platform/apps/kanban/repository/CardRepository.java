package fr.epita.platform.apps.kanban.repository;

import fr.epita.platform.apps.kanban.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findByColumnIdOrderByPositionAsc(UUID columnId);
    List<Card> findByColumnIdInOrderByPositionAsc(java.util.Collection<UUID> columnIds);
    long countByColumnId(UUID columnId);
    List<Card> findByAssigneeId(UUID assigneeId);
}
