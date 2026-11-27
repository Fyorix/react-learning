package fr.epita.platform.apps.defense.repository;

import fr.epita.platform.apps.defense.entity.DefenseSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DefenseSlotRepository extends JpaRepository<DefenseSlot, UUID> {

    List<DefenseSlot> findAllByOrderByStartAtAsc();

    List<DefenseSlot> findByStartAtBetweenOrderByStartAtAsc(Instant from, Instant to);

    @Query("select distinct s from DefenseSlot s join s.juryIds j where j = :t order by s.startAt")
    List<DefenseSlot> ofTeacher(@Param("t") java.util.UUID teacherId);

    /** Verrou du créneau : deux groupes qui le choisissent en même temps, un seul réussit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DefenseSlot s where s.id = :id")
    Optional<DefenseSlot> lock(@Param("id") UUID id);
}
