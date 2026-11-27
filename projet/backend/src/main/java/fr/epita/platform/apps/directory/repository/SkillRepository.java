package fr.epita.platform.apps.directory.repository;

import fr.epita.platform.apps.directory.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
