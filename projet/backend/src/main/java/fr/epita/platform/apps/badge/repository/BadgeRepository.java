package fr.epita.platform.apps.badge.repository;

import fr.epita.platform.apps.badge.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    List<Badge> findAllByOrderByNameAsc();

    List<Badge> findByEvent(String event);

    boolean existsByCode(String code);
}
