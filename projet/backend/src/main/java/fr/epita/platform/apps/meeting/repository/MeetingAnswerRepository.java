package fr.epita.platform.apps.meeting.repository;

import fr.epita.platform.apps.meeting.entity.MeetingAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MeetingAnswerRepository extends JpaRepository<MeetingAnswer, MeetingAnswer.Key> {

    List<MeetingAnswer> findByIdSlotIdIn(Collection<UUID> slotIds);
}
