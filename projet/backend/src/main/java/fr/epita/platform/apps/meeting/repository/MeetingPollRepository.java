package fr.epita.platform.apps.meeting.repository;

import fr.epita.platform.apps.meeting.entity.MeetingPoll;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingPollRepository extends JpaRepository<MeetingPoll, UUID> {

    List<MeetingPoll> findByOrganizerIdOrderByCreatedAtDesc(UUID organizerId);

    @Query("select p from MeetingPoll p join p.participantIds u where u = :u order by p.createdAt desc")
    List<MeetingPoll> ofParticipant(@Param("u") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MeetingPoll p where p.id = :id")
    Optional<MeetingPoll> lock(@Param("id") UUID id);
}
