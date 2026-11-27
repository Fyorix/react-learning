package fr.epita.platform.apps.timetable.repository;

import fr.epita.platform.apps.timetable.entity.TimetableRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimetableRoomRepository extends JpaRepository<TimetableRoom, UUID> {

    List<TimetableRoom> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
