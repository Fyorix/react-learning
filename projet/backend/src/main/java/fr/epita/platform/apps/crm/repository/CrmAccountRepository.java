package fr.epita.platform.apps.crm.repository;

import fr.epita.platform.apps.crm.entity.CrmAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CrmAccountRepository extends JpaRepository<CrmAccount, UUID> {

    List<CrmAccount> findAllByOrderByNameAsc();
}
