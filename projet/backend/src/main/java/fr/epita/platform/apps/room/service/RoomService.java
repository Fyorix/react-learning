package fr.epita.platform.apps.room.service;

import fr.epita.platform.apps.room.dto.RoomDtos.BookingDto;
import fr.epita.platform.apps.room.dto.RoomDtos.BookingRequest;
import fr.epita.platform.apps.room.dto.RoomDtos.OccupancyDto;
import fr.epita.platform.apps.room.dto.RoomDtos.RoomDto;
import fr.epita.platform.apps.room.dto.RoomDtos.RoomRequest;
import fr.epita.platform.apps.room.entity.Equipment;
import fr.epita.platform.apps.room.entity.Room;
import fr.epita.platform.apps.room.entity.RoomBooking;
import fr.epita.platform.apps.room.repository.RoomBookingRepository;
import fr.epita.platform.apps.room.repository.RoomRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.CalendarRules;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    static final LocalTime OPENING = LocalTime.of(8, 0);
    static final LocalTime CLOSING = LocalTime.of(20, 0);
    static final Duration MAX_DURATION = Duration.ofHours(4);
    static final int MAX_DAYS_AHEAD = 30;

    private final RoomRepository rooms;
    private final RoomBookingRepository bookings;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- salles ----------

    @Transactional(readOnly = true)
    public List<RoomDto> list(Equipment equipment, Integer minCapacity, boolean includeInactive) {
        return rooms.findAllByOrderByNameAsc().stream()
                .filter(r -> includeInactive || r.isActive())
                .filter(r -> equipment == null || r.getEquipment().contains(equipment))
                .filter(r -> minCapacity == null || r.getCapacity() >= minCapacity)
                .map(RoomService::toDto).toList();
    }

    @Transactional
    public RoomDto create(AuthUser me, RoomRequest req) {
        me.requireAdmin();
        if (rooms.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("ROOM_NAME_EXISTS", "Une salle porte déjà ce nom");
        }
        Room room = new Room();
        apply(room, req);
        return toDto(rooms.saveAndFlush(room));
    }

    /** Désactiver une salle annule ses réservations futures. */
    @Transactional
    public RoomDto update(AuthUser me, UUID id, RoomRequest req) {
        me.requireAdmin();
        Room room = room(id);
        if (rooms.existsByNameIgnoreCaseAndIdNot(req.name().trim(), id)) {
            throw ApiException.conflict("ROOM_NAME_EXISTS", "Une salle porte déjà ce nom");
        }
        boolean wasActive = room.isActive();
        apply(room, req);
        if (wasActive && !room.isActive()) {
            bookings.deleteAll(bookings.findByRoomIdAndStartsAtAfter(id, clock.instant()));
        }
        return toDto(room);
    }

    // ---------- réservations ----------

    @Transactional(readOnly = true)
    public List<BookingDto> dayBookings(AuthUser me, UUID roomId, LocalDate date) {
        room(roomId);
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        ZoneId zone = clock.getZone();
        return toDtos(bookings.overlapping(roomId, day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant()), me);
    }

    @Transactional(readOnly = true)
    public List<RoomDto> available(Instant start, Instant end, Integer attendees, Set<Equipment> equipment) {
        checkSlot(start, end);
        return rooms.findAllByOrderByNameAsc().stream()
                .filter(Room::isActive)
                .filter(r -> attendees == null || r.getCapacity() >= attendees)
                .filter(r -> equipment == null || r.getEquipment().containsAll(equipment))
                .filter(r -> bookings.overlapping(r.getId(), start, end).isEmpty())
                .map(RoomService::toDto).toList();
    }

    @Transactional
    public BookingDto book(AuthUser me, UUID roomId, BookingRequest req) {
        Room room = rooms.lock(roomId).orElseThrow(() -> ApiException.notFound("Salle"));
        RoomBooking b = new RoomBooking();
        b.setRoomId(roomId);
        b.setUserId(me.id());
        applyBooking(room, b, req, null);
        return toDtos(List.of(bookings.saveAndFlush(b)), me).get(0);
    }

    @Transactional
    public BookingDto update(AuthUser me, UUID bookingId, BookingRequest req) {
        RoomBooking b = booking(bookingId);
        me.requireOwnerOrAdmin(b.getUserId());
        Room room = rooms.lock(b.getRoomId()).orElseThrow(() -> ApiException.notFound("Salle"));
        applyBooking(room, b, req, b.getId());
        return toDtos(List.of(bookings.saveAndFlush(b)), me).get(0);
    }

    @Transactional
    public void cancel(AuthUser me, UUID bookingId) {
        RoomBooking b = booking(bookingId);
        me.requireOwnerOrAdmin(b.getUserId());
        bookings.delete(b);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> mine(AuthUser me) {
        return toDtos(bookings.findByUserIdAndEndsAtAfterOrderByStartsAt(me.id(), clock.instant()), me);
    }

    /** Taux d'occupation : heures réservées / heures d'ouverture (12 h par jour ouvré) sur la période. */
    @Transactional(readOnly = true)
    public List<OccupancyDto> occupancy(AuthUser me, LocalDate from, LocalDate to) {
        me.requireAdmin();
        LocalDate start = from == null ? LocalDate.now(clock).withDayOfMonth(1) : from;
        LocalDate end = to == null ? start.plusMonths(1).minusDays(1) : to;
        if (end.isBefore(start)) {
            throw ValidationException.field("to", "doit être postérieur ou égal à from");
        }
        long workingDays = start.datesUntil(end.plusDays(1)).filter(CalendarRules::isWorkingDay).count();
        double available = workingDays * Duration.between(OPENING, CLOSING).toHours();
        ZoneId zone = clock.getZone();
        List<RoomBooking> all = bookings.between(start.atStartOfDay(zone).toInstant(),
                end.plusDays(1).atStartOfDay(zone).toInstant());
        return rooms.findAllByOrderByNameAsc().stream().map(r -> {
            double hours = all.stream().filter(b -> b.getRoomId().equals(r.getId()))
                    .mapToLong(b -> Duration.between(b.getStartsAt(), b.getEndsAt()).toMinutes()).sum() / 60.0;
            int rate = available == 0 ? 0 : (int) Math.round(hours * 100 / available);
            return new OccupancyDto(r.getId(), r.getName(), hours, available, rate);
        }).toList();
    }

    // ---------- règles ----------

    private void applyBooking(Room room, RoomBooking b, BookingRequest req, UUID excludedId) {
        if (!room.isActive()) {
            throw ApiException.conflict("ROOM_INACTIVE", "Cette salle n'est plus réservable");
        }
        checkSlot(req.start(), req.end());
        Instant now = clock.instant();
        if (!req.start().isAfter(now) || req.start().isAfter(now.plus(Duration.ofDays(MAX_DAYS_AHEAD)))) {
            throw ApiException.conflict("BOOKING_OUT_OF_RANGE",
                    "Une réservation commence dans le futur et au plus " + MAX_DAYS_AHEAD + " jours à l'avance");
        }
        if (req.attendees() > room.getCapacity()) {
            throw ApiException.conflict("CAPACITY_EXCEEDED", "La salle n'a que " + room.getCapacity() + " places");
        }
        boolean overlap = bookings.overlapping(room.getId(), req.start(), req.end()).stream()
                .anyMatch(o -> !o.getId().equals(excludedId));
        if (overlap) {
            throw ApiException.conflict("ROOM_ALREADY_BOOKED", "La salle est déjà réservée sur ce créneau");
        }
        b.setStartsAt(req.start());
        b.setEndsAt(req.end());
        b.setTitle(req.title().trim());
        b.setAttendees(req.attendees());
    }

    /** Créneau : aligné sur 30 minutes, même jour, entre 8 h et 20 h, de 30 min à 4 h. */
    void checkSlot(Instant start, Instant end) {
        ZoneId zone = clock.getZone();
        ZonedDateTime s = start.atZone(zone);
        ZonedDateTime e = end.atZone(zone);
        var errors = new ValidationException.Collector();
        errors.check(aligned(s), "start", "doit être aligné sur 30 minutes");
        errors.check(aligned(e), "end", "doit être aligné sur 30 minutes");
        errors.check(e.isAfter(s), "end", "doit être postérieur au début");
        errors.check(s.toLocalDate().equals(e.toLocalDate()), "end", "doit être le même jour que le début");
        errors.check(!s.toLocalTime().isBefore(OPENING) && !e.toLocalTime().isAfter(CLOSING)
                && !e.toLocalTime().equals(LocalTime.MIDNIGHT), "start", "le créneau doit être compris entre 8 h et 20 h");
        errors.check(Duration.between(s, e).compareTo(MAX_DURATION) <= 0, "end", "4 heures maximum");
        errors.throwIfAny();
    }

    private static boolean aligned(ZonedDateTime t) {
        return t.getSecond() == 0 && t.getNano() == 0 && t.getMinute() % 30 == 0;
    }

    private static void apply(Room room, RoomRequest req) {
        room.setName(req.name().trim());
        room.setFloor(req.floor().trim());
        room.setCapacity(req.capacity());
        room.setEquipment(req.equipment().isEmpty() ? EnumSet.noneOf(Equipment.class) : EnumSet.copyOf(req.equipment()));
        room.setActive(req.active() == null || req.active());
    }

    private Room room(UUID id) {
        return rooms.findById(id).orElseThrow(() -> ApiException.notFound("Salle"));
    }

    private RoomBooking booking(UUID id) {
        return bookings.findById(id).orElseThrow(() -> ApiException.notFound("Réservation"));
    }

    private static RoomDto toDto(Room r) {
        return new RoomDto(r.getId(), r.getName(), r.getFloor(), r.getCapacity(), r.getEquipment(), r.isActive());
    }

    private List<BookingDto> toDtos(List<RoomBooking> list, AuthUser me) {
        Map<UUID, String> names = userLookup.namesOf(list, RoomBooking::getUserId);
        Map<UUID, String> roomNames = rooms.findAllById(list.stream().map(RoomBooking::getRoomId).distinct().toList())
                .stream().collect(Collectors.toMap(Room::getId, Room::getName));
        return list.stream().map(b -> new BookingDto(b.getId(), b.getRoomId(), roomNames.get(b.getRoomId()),
                b.getUserId(), names.get(b.getUserId()), b.getTitle(), b.getAttendees(), b.getStartsAt(),
                b.getEndsAt(), b.getUserId().equals(me.id()), me.isAdmin() || b.getUserId().equals(me.id())))
                .toList();
    }
}
