package fr.epita.platform.apps.form.repository;

import fr.epita.platform.apps.form.entity.FormRespondent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormRespondentRepository extends JpaRepository<FormRespondent, FormRespondent.Key> {
}
