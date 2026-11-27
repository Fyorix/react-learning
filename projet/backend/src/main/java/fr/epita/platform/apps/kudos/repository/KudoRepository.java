package fr.epita.platform.apps.kudos.repository;

import fr.epita.platform.apps.kudos.entity.Kudo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface KudoRepository extends JpaRepository<Kudo, UUID>, JpaSpecificationExecutor<Kudo> {

    long countByAuthorIdAndCreatedAtGreaterThanEqual(UUID authorId, Instant since);

    List<Kudo> findByModeratedFalseAndCreatedAtBetween(Instant from, Instant to);

    List<Kudo> findByAuthorIdAndModeratedFalse(UUID authorId);

    @Query("select k from Kudo k where k.moderated = false and :userId member of k.recipientIds")
    List<Kudo> received(@Param("userId") UUID userId);
}
