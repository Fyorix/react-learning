package fr.epita.platform.apps.association.repository;

import fr.epita.platform.apps.association.entity.Assembly;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssemblyRepository extends JpaRepository<Assembly, UUID> {

    List<Assembly> findAllByOrderByDateDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assembly a where a.id = :id")
    Optional<Assembly> lock(@Param("id") UUID id);
}
