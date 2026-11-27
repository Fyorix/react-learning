package fr.epita.platform.apps.poll.repository;

import fr.epita.platform.apps.poll.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PollVoteRepository extends JpaRepository<PollVote, UUID> {

    List<PollVote> findByPollId(UUID pollId);

    List<PollVote> findByPollIdAndUserId(UUID pollId, UUID userId);

    List<PollVote> findByPollIdIn(Collection<UUID> pollIds);

    boolean existsByPollId(UUID pollId);

    @Modifying
    @Query("delete from PollVote v where v.pollId = :pollId and v.userId = :userId")
    int deleteVote(UUID pollId, UUID userId);
}
