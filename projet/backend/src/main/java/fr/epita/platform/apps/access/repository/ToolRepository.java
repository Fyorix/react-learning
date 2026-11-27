package fr.epita.platform.apps.access.repository;

import fr.epita.platform.apps.access.entity.Tool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ToolRepository extends JpaRepository<Tool, UUID> {

    List<Tool> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
