package fr.epita.platform.apps.directory.repository;

import fr.epita.platform.apps.directory.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
