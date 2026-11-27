package fr.epita.platform.apps.kanban.repository;

import fr.epita.platform.apps.kanban.entity.CardMove;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CardMoveRepository extends JpaRepository<CardMove, UUID> {

    List<CardMove> findByCardIdOrderByCreatedAtAsc(UUID cardId);
}
