package fr.epita.platform.apps.pomodoro.repository;

import fr.epita.platform.apps.pomodoro.entity.CompletedPomodoro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CompletedPomodoroRepository extends JpaRepository<CompletedPomodoro, UUID> {

    List<CompletedPomodoro> findByUserIdAndCompletedAtAfter(UUID userId, Instant after);
}
