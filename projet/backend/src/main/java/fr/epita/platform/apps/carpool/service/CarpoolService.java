package fr.epita.platform.apps.carpool.service;

import fr.epita.platform.apps.carpool.dto.CarpoolDtos.TripDto;
import fr.epita.platform.apps.carpool.dto.CarpoolDtos.TripRequest;
import fr.epita.platform.apps.carpool.entity.Booking;
import fr.epita.platform.apps.carpool.entity.Trip;
import fr.epita.platform.apps.carpool.entity.TripStatus;
import fr.epita.platform.apps.carpool.repository.BookingRepository;
import fr.epita.platform.apps.carpool.repository.TripRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.user.dto.UserDtos.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarpoolService {

    private final TripRepository trips;
    private final BookingRepository bookings;
    private final UserLookup userLookup;
    private final Clock clock;

    @Transactional
    public TripDto create(AuthUser me, TripRequest req) {
        requireFuture(req.departureAt());
        Trip trip = new Trip();
        trip.setDriverId(me.id());
        apply(trip, req);
        return toDto(trips.saveAndFlush(trip), me);
    }

    @Transactional(readOnly = true)
    public PageResponse<TripDto> search(AuthUser me, String from, String to, LocalDate date, int page, int size) {
        ZoneId zone = clock.getZone();
        List<Trip> found = trips.findByDepartureAtAfterOrderByDepartureAtAsc(clock.instant()).stream()
                .filter(t -> t.getStatus() == TripStatus.ACTIVE)
                .filter(t -> matches(t.getFromCity(), from) && matches(t.getToCity(), to))
                .filter(t -> date == null || t.getDepartureAt().atZone(zone).toLocalDate().equals(date))
                .toList();
        return PageResponse.ofList(toDtos(found, me), page, size);
    }

    @Transactional(readOnly = true)
    public TripDto get(AuthUser me, UUID id) {
        return toDto(find(id), me);
    }

    @Transactional
    public TripDto update(AuthUser me, UUID id, TripRequest req) {
        Trip trip = trips.lockById(id).orElseThrow(() -> ApiException.notFound("Trajet"));
        me.requireOwnerOrAdmin(trip.getDriverId());
        if (trip.getStatus() == TripStatus.CANCELLED) {
            throw ApiException.conflict("TRIP_CANCELLED", "Le trajet est annulé");
        }
        requireFuture(req.departureAt());
        long booked = bookings.countByTripId(id);
        if (req.seats() < booked) {
            throw ApiException.conflict("SEATS_BELOW_BOOKINGS",
                    "Il y a déjà " + booked + " réservation(s) : impossible de descendre à " + req.seats() + " place(s)");
        }
        apply(trip, req);
        return toDto(trips.saveAndFlush(trip), me);
    }

    @Transactional
    public TripDto cancel(AuthUser me, UUID id) {
        Trip trip = find(id);
        me.requireOwnerOrAdmin(trip.getDriverId());
        trip.setStatus(TripStatus.CANCELLED);
        return toDto(trips.saveAndFlush(trip), me);
    }

    /**
     * Réservation d'une place. Le trajet est verrouillé (FOR UPDATE) : deux réservations simultanées
     * de la dernière place ne peuvent pas réussir toutes les deux.
     */
    @Transactional
    public TripDto book(AuthUser me, UUID id) {
        Trip trip = trips.lockById(id).orElseThrow(() -> ApiException.notFound("Trajet"));
        if (trip.getStatus() == TripStatus.CANCELLED || !trip.getDepartureAt().isAfter(clock.instant())) {
            throw ApiException.conflict("TRIP_NOT_BOOKABLE", "Ce trajet n'est plus réservable");
        }
        if (trip.getDriverId().equals(me.id())) {
            throw ApiException.conflict("OWN_TRIP", "Impossible de réserver son propre trajet");
        }
        if (bookings.findByTripIdAndPassengerId(id, me.id()).isPresent()) {
            throw ApiException.conflict("ALREADY_BOOKED", "Vous avez déjà une place sur ce trajet");
        }
        if (bookings.countByTripId(id) >= trip.getSeats()) {
            throw ApiException.conflict("NO_SEAT_AVAILABLE", "Plus aucune place disponible");
        }
        Booking booking = new Booking();
        booking.setTripId(id);
        booking.setPassengerId(me.id());
        bookings.saveAndFlush(booking);
        return toDto(trip, me);
    }

    @Transactional
    public TripDto cancelBooking(AuthUser me, UUID id) {
        Trip trip = find(id);
        Booking booking = bookings.findByTripIdAndPassengerId(id, me.id())
                .orElseThrow(() -> ApiException.notFound("Réservation"));
        if (!trip.getDepartureAt().isAfter(clock.instant())) {
            throw ApiException.conflict("TRIP_NOT_BOOKABLE", "Le trajet est passé");
        }
        bookings.delete(booking);
        bookings.flush();
        return toDto(trip, me);
    }

    @Transactional(readOnly = true)
    public List<TripDto> myTrips(AuthUser me, String role) {
        if ("passenger".equals(role)) {
            Set<UUID> ids = bookings.findByPassengerId(me.id()).stream().map(Booking::getTripId)
                    .collect(Collectors.toSet());
            return toDtos(trips.findByIdInOrderByDepartureAtDesc(ids), me);
        }
        if (role != null && !"driver".equals(role)) {
            throw ValidationException.field("role", "Valeurs possibles : driver, passenger");
        }
        return toDtos(trips.findByDriverIdOrderByDepartureAtDesc(me.id()), me);
    }

    @Transactional(readOnly = true)
    public List<TripDto> adminTrips(AuthUser me, YearMonth month) {
        YearMonth m = month != null ? month : YearMonth.now(clock);
        ZoneId zone = clock.getZone();
        Instant from = m.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        return toDtos(trips.findByDepartureAtBetweenOrderByDepartureAtAsc(from, to), me);
    }

    private void requireFuture(Instant departureAt) {
        if (!departureAt.isAfter(clock.instant())) {
            throw ValidationException.field("departureAt", "Le départ doit être dans le futur");
        }
    }

    private void apply(Trip trip, TripRequest req) {
        trip.setFromCity(req.from().trim());
        trip.setToCity(req.to().trim());
        trip.setDepartureAt(req.departureAt());
        trip.setSeats(req.seats());
        trip.setComment(req.comment() == null || req.comment().isBlank() ? null : req.comment().trim());
    }

    private static boolean matches(String value, String filter) {
        return filter == null || filter.isBlank() || value.toLowerCase().contains(filter.trim().toLowerCase());
    }

    private Trip find(UUID id) {
        return trips.findById(id).orElseThrow(() -> ApiException.notFound("Trajet"));
    }

    private TripDto toDto(Trip trip, AuthUser me) {
        return toDtos(List.of(trip), me).getFirst();
    }

    private List<TripDto> toDtos(List<Trip> list, AuthUser me) {
        Map<UUID, List<Booking>> byTrip = bookings.findByTripIdIn(list.stream().map(Trip::getId).toList()).stream()
                .collect(Collectors.groupingBy(Booking::getTripId));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(t -> userIds.add(t.getDriverId()));
        byTrip.values().forEach(bs -> bs.forEach(b -> userIds.add(b.getPassengerId())));
        Map<UUID, String> names = userLookup.names(userIds);
        Instant now = clock.instant();
        return list.stream().map(t -> {
            List<Booking> bs = byTrip.getOrDefault(t.getId(), List.of());
            boolean isDriver = t.getDriverId().equals(me.id());
            List<UserSummary> passengers = isDriver || me.isAdmin()
                    ? bs.stream().map(b -> new UserSummary(b.getPassengerId(), names.get(b.getPassengerId()))).toList()
                    : null;
            return new TripDto(t.getId(), new UserSummary(t.getDriverId(), names.get(t.getDriverId())),
                    t.getFromCity(), t.getToCity(), t.getDepartureAt(), t.getSeats(),
                    Math.max(0, t.getSeats() - bs.size()), t.getComment(), t.getStatus(),
                    !t.getDepartureAt().isAfter(now),
                    bs.stream().anyMatch(b -> b.getPassengerId().equals(me.id())), isDriver, passengers);
        }).toList();
    }
}
