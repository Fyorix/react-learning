package fr.epita.platform.apps.supply.repository;

import fr.epita.platform.apps.supply.entity.Restock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RestockRepository extends JpaRepository<Restock, UUID> {
}
