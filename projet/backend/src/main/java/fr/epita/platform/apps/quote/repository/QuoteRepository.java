package fr.epita.platform.apps.quote.repository;

import fr.epita.platform.apps.quote.entity.Quote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    List<Quote> findAllByOrderByCreatedAtDesc();

    List<Quote> findBySalesIdOrderByCreatedAtDesc(UUID salesId);

    List<Quote> findByClientIdOrderByCreatedAtDesc(UUID clientId);
}
