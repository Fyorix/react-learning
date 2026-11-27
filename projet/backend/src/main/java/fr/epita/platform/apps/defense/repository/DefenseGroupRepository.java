package fr.epita.platform.apps.defense.repository;

import fr.epita.platform.apps.defense.entity.DefenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DefenseGroupRepository extends JpaRepository<DefenseGroup, UUID> {

    List<DefenseGroup> findAllByOrderByNameAsc();

    Optional<DefenseGroup> findBySlotId(UUID slotId);

    boolean existsByNameIgnoreCase(String name);

    @Query("select g from DefenseGroup g join g.studentIds s where s = :u")
    Optional<DefenseGroup> ofStudent(@Param("u") UUID studentId);
}
