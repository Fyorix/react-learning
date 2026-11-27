package fr.epita.platform.apps.training.repository;

import fr.epita.platform.apps.training.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    List<Enrollment> findBySessionId(UUID sessionId);

    List<Enrollment> findByUserId(UUID userId);

    List<Enrollment> findByUserIdIn(Collection<UUID> userIds);
}
