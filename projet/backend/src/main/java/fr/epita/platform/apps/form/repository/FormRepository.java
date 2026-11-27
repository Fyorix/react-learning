package fr.epita.platform.apps.form.repository;

import fr.epita.platform.apps.form.entity.Form;
import fr.epita.platform.apps.form.entity.FormStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormRepository extends JpaRepository<Form, UUID> {

    List<Form> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<Form> findByStatusOrderByCreatedAtDesc(FormStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Form f where f.id = :id")
    Optional<Form> lock(@Param("id") UUID id);
}
