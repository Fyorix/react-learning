package fr.epita.platform.apps.skills.repository;

import fr.epita.platform.apps.skills.entity.CatalogSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CatalogSkillRepository extends JpaRepository<CatalogSkill, UUID> {

    List<CatalogSkill> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
