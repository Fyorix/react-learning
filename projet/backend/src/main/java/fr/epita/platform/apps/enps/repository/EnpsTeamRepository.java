package fr.epita.platform.apps.enps.repository;

import fr.epita.platform.apps.enps.entity.EnpsTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnpsTeamRepository extends JpaRepository<EnpsTeam, UUID> {

    List<EnpsTeam> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
