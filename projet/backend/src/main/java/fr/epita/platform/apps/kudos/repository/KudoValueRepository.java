package fr.epita.platform.apps.kudos.repository;

import fr.epita.platform.apps.kudos.entity.KudoValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KudoValueRepository extends JpaRepository<KudoValue, UUID> {

    List<KudoValue> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
