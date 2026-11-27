package fr.epita.platform.apps.form.repository;

import fr.epita.platform.apps.form.entity.FormResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FormResponseRepository extends JpaRepository<FormResponse, UUID> {

    Page<FormResponse> findByFormIdAndVersionOrderByCreatedAtDesc(UUID formId, int version, Pageable page);

    List<FormResponse> findByFormIdAndVersion(UUID formId, int version);

    long countByFormId(UUID formId);
}
