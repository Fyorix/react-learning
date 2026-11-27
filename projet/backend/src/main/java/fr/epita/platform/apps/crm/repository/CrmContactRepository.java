package fr.epita.platform.apps.crm.repository;

import fr.epita.platform.apps.crm.entity.CrmContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CrmContactRepository extends JpaRepository<CrmContact, UUID> {

    List<CrmContact> findByAccountIdOrderByLastNameAscFirstNameAsc(UUID accountId);

    boolean existsByAccountIdAndEmail(UUID accountId, String email);
}
