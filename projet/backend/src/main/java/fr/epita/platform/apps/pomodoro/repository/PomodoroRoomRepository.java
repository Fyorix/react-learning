package fr.epita.platform.apps.pomodoro.repository;

import fr.epita.platform.apps.pomodoro.entity.PomodoroRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PomodoroRoomRepository extends JpaRepository<PomodoroRoom, UUID> {

    List<PomodoroRoom> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PomodoroRoom r where r.id = :id")
    Optional<PomodoroRoom> lock(@Param("id") UUID id);
}
