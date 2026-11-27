package fr.epita.platform.apps.meeting.repository;

import fr.epita.platform.apps.meeting.entity.MeetingSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MeetingSlotRepository extends JpaRepository<MeetingSlot, UUID> {

    List<MeetingSlot> findByPollIdOrderByStartAtAsc(UUID pollId);

    void deleteByPollId(UUID pollId);
}
