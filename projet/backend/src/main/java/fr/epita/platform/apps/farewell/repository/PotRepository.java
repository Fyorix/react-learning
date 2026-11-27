package fr.epita.platform.apps.farewell.repository;

import fr.epita.platform.apps.farewell.entity.Pot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PotRepository extends JpaRepository<Pot, UUID> {

    @Query("select distinct p from Pot p left join p.inviteeIds i where p.organizerId = :u or p.beneficiaryId = :u or i = :u order by p.createdAt desc")
    List<Pot> involving(@Param("u") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pot p where p.id = :id")
    Optional<Pot> lock(@Param("id") UUID id);
}
