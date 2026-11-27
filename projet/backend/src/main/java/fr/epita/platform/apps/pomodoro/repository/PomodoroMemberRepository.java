package fr.epita.platform.apps.pomodoro.repository;

import fr.epita.platform.apps.pomodoro.entity.PomodoroMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PomodoroMemberRepository extends JpaRepository<PomodoroMember, PomodoroMember.Key> {

    List<PomodoroMember> findByIdRoomId(UUID roomId);

    List<PomodoroMember> findByIdUserId(UUID userId);
}
