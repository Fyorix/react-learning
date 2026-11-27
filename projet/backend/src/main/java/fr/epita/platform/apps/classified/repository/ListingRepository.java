package fr.epita.platform.apps.classified.repository;

import fr.epita.platform.apps.classified.entity.Listing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    List<Listing> findAllByOrderByCreatedAtDesc();

    List<Listing> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);

    List<Listing> findByHiddenTrueOrderByCreatedAtDesc();
}
