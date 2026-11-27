package fr.epita.platform.apps.retro.repository;

import fr.epita.platform.apps.retro.entity.RetroCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RetroCardRepository extends JpaRepository<RetroCard, UUID> {

    List<RetroCard> findByRetroIdOrderByCreatedAtAsc(UUID retroId);
}
