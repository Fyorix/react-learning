package fr.epita.platform.apps.association.repository;

import fr.epita.platform.apps.association.entity.AssociationFee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssociationFeeRepository extends JpaRepository<AssociationFee, UUID> {

    boolean existsByUserIdAndSeason(UUID userId, String season);

    List<AssociationFee> findByUserIdOrderBySeasonDesc(UUID userId);

    List<AssociationFee> findBySeason(String season);
}
