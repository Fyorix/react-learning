package fr.epita.platform.apps.retro.repository;

import fr.epita.platform.apps.retro.entity.RetroGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RetroGroupRepository extends JpaRepository<RetroGroup, UUID> {

    List<RetroGroup> findByRetroIdOrderByCreatedAtAsc(UUID retroId);
}
