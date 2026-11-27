package fr.epita.platform.apps.mood.repository;

import fr.epita.platform.apps.mood.entity.MoodEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MoodEntryRepository extends JpaRepository<MoodEntry, UUID> {

    Optional<MoodEntry> findByUserIdAndDate(UUID userId, LocalDate date);

    List<MoodEntry> findByUserIdAndDateBetweenOrderByDateAsc(UUID userId, LocalDate from, LocalDate to);

    List<MoodEntry> findByUserIdOrderByDateDesc(UUID userId);

    List<MoodEntry> findByDateBetween(LocalDate from, LocalDate to);

    long countByDateBetween(LocalDate from, LocalDate to);
}
