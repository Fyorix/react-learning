package fr.epita.platform.apps.room.repository;

import fr.epita.platform.apps.room.entity.RoomBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RoomBookingRepository extends JpaRepository<RoomBooking, UUID> {

    @Query("""
            select b from RoomBooking b
            where b.roomId = :roomId and b.startsAt < :end and b.endsAt > :start
            order by b.startsAt
            """)
    List<RoomBooking> overlapping(@Param("roomId") UUID roomId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("select b from RoomBooking b where b.startsAt < :end and b.endsAt > :start order by b.startsAt")
    List<RoomBooking> between(@Param("start") Instant start, @Param("end") Instant end);

    List<RoomBooking> findByUserIdAndEndsAtAfterOrderByStartsAt(UUID userId, Instant after);

    List<RoomBooking> findByRoomIdAndStartsAtAfter(UUID roomId, Instant after);
}
