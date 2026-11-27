package fr.epita.platform.apps.association.repository;

import fr.epita.platform.apps.association.entity.Ballot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BallotRepository extends JpaRepository<Ballot, Ballot.Key> {

    List<Ballot> findByIdResolutionIdIn(Collection<UUID> resolutionIds);

    @Query("select count(distinct b.id.userId) from Ballot b where b.id.resolutionId in :ids")
    long countVoters(@Param("ids") Collection<UUID> resolutionIds);
}
