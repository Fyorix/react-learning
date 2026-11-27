package fr.epita.platform.apps.kanban.repository;

import fr.epita.platform.apps.kanban.entity.Board;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {

    List<Board> findAllByOrderByNameAsc();

    /** Verrou du tableau pendant un déplacement : les positions restent cohérentes en cas d'appels simultanés. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Board b where b.id = :id")
    Optional<Board> lock(@Param("id") UUID id);
}
