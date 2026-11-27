package fr.epita.platform.apps.volunteering.repository;

import fr.epita.platform.apps.volunteering.entity.Association;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssociationRepository extends JpaRepository<Association, UUID> {

    List<Association> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
