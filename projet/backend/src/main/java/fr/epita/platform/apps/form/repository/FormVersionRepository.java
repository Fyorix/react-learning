package fr.epita.platform.apps.form.repository;

import fr.epita.platform.apps.form.entity.FormVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FormVersionRepository extends JpaRepository<FormVersion, UUID> {

    Optional<FormVersion> findByFormIdAndNumber(UUID formId, int number);
}
