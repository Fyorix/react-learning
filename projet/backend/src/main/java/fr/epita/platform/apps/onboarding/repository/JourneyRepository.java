package fr.epita.platform.apps.onboarding.repository;

import fr.epita.platform.apps.onboarding.entity.Journey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JourneyRepository extends JpaRepository<Journey, UUID> {

    List<Journey> findAllByOrderByStartDateDesc();

    List<Journey> findByNewcomerIdOrderByStartDateDesc(UUID newcomerId);
}
