package fr.epita.platform.apps.classified.repository;

import fr.epita.platform.apps.classified.entity.ListingMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingMessageRepository extends JpaRepository<ListingMessage, UUID> {

    List<ListingMessage> findBySenderIdOrRecipientIdOrderByCreatedAtAsc(UUID senderId, UUID recipientId);
}
