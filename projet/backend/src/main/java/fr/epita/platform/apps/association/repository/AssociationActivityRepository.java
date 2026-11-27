package fr.epita.platform.apps.association.repository;

import fr.epita.platform.apps.association.entity.AssociationActivity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssociationActivityRepository extends JpaRepository<AssociationActivity, UUID> {

    List<AssociationActivity> findAllByOrderByDateAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AssociationActivity a where a.id = :id")
    Optional<AssociationActivity> lock(@Param("id") UUID id);
}
