package fr.epita.platform.apps.license.repository;

import fr.epita.platform.apps.license.entity.Software;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SoftwareRepository extends JpaRepository<Software, UUID> {

    List<Software> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    /** Verrou du logiciel : jamais plus de sièges attribués que `seats`, même en concurrence. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Software s where s.id = :id")
    Optional<Software> lock(@Param("id") UUID id);
}
