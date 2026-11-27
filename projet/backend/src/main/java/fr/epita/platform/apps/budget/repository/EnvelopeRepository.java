package fr.epita.platform.apps.budget.repository;

import fr.epita.platform.apps.budget.entity.Envelope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnvelopeRepository extends JpaRepository<Envelope, Envelope.Key> {

    List<Envelope> findByIdTeamIdAndIdYear(UUID teamId, Integer year);
}
