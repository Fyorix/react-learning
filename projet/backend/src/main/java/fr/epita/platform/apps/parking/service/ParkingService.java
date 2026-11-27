package fr.epita.platform.apps.parking.service;

import fr.epita.platform.apps.parking.dto.ParkingDtos.DayDto;
import fr.epita.platform.apps.parking.dto.ParkingDtos.MeDto;
import fr.epita.platform.apps.parking.dto.ParkingDtos.MyBookingDto;
import fr.epita.platform.apps.parking.dto.ParkingDtos.SpotDayDto;
import fr.epita.platform.apps.parking.dto.ParkingDtos.SpotDto;
import fr.epita.platform.apps.parking.dto.ParkingDtos.SpotRequest;
import fr.epita.platform.apps.parking.dto.ParkingDtos.UsageDto;
import fr.epita.platform.apps.parking.entity.Spot;
import fr.epita.platform.apps.parking.entity.SpotBooking;
import fr.epita.platform.apps.parking.entity.SpotRelease;
import fr.epita.platform.apps.parking.repository.SpotBookingRepository;
import fr.epita.platform.apps.parking.repository.SpotReleaseRepository;
import fr.epita.platform.apps.parking.repository.SpotRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.CalendarRules;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParkingService {

    static final int MAX_DAYS_AHEAD = 7;

    private final SpotRepository spots;
    private final SpotReleaseRepository releases;
    private final SpotBookingRepository bookings;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- situation d'un jour ----------

    @Transactional(readOnly = true)
    public DayDto day(AuthUser me, LocalDate day) {
        Set<UUID> released = releases.findByIdDay(day).stream().map(r -> r.getId().getSpotId()).collect(Collectors.toSet());
        Map<UUID, SpotBooking> booked = bookings.findByDay(day).stream().collect(Collectors.toMap(SpotBooking::getSpotId, Function.identity()));
        List<SpotDayDto> list = spots.findAllByOrderByNumberAsc().stream().map(s -> {
            SpotBooking b = booked.get(s.getId());
            String state;
            UUID occupant = null;
            if (b != null) {
                state = "BOOKED";
                occupant = b.getUserId();
            } else if (s.getHolderId() != null && !released.contains(s.getId())) {
                state = "HOLDER";
                occupant = s.getHolderId();
            } else {
                state = s.getHolderId() != null ? "RELEASED" : "FREE";
            }
            return new SpotDayDto(s.getId(), s.getNumber(), s.getLevel(), state, s.getHolderId(), userLookup.name(s.getHolderId()),
                    occupant, userLookup.name(occupant), me.id().equals(occupant));
        }).toList();
        long free = list.stream().filter(s -> s.state().equals("FREE") || s.state().equals("RELEASED")).count();
        return new DayDto(day, CalendarRules.isWorkingDay(day), free, list);
    }

    @Transactional(readOnly = true)
    public MeDto me(AuthUser me) {
        LocalDate today = LocalDate.now(clock);
        Optional<Spot> spot = spots.findByHolderId(me.id());
        List<LocalDate> rel = spot.map(s -> releases.findByIdSpotIdAndIdDayGreaterThanEqualOrderByIdDayAsc(s.getId(), today)
                .stream().map(r -> r.getId().getDay()).toList()).orElse(List.of());
        List<LocalDate> taken = spot.map(s -> rel.stream().filter(d -> bookings.findBySpotIdAndDay(s.getId(), d).isPresent()).toList()).orElse(List.of());
        Map<UUID, Spot> byId = spots.findAll().stream().collect(Collectors.toMap(Spot::getId, Function.identity()));
        List<MyBookingDto> mine = bookings.findByUserIdAndDayGreaterThanEqualOrderByDayAsc(me.id(), today).stream()
                .map(b -> new MyBookingDto(b.getId(), b.getDay(), byId.get(b.getSpotId()).getNumber(), byId.get(b.getSpotId()).getLevel())).toList();
        return new MeDto(spot.map(this::toDto).orElse(null), rel, taken, mine);
    }

    // ---------- titulaire ----------

    @Transactional
    public MeDto release(AuthUser me, List<LocalDate> dates) {
        Spot s = spots.findByHolderId(me.id()).orElseThrow(() -> ApiException.forbidden("Vous n'êtes titulaire d'aucune place"));
        LocalDate today = LocalDate.now(clock);
        var errors = new ValidationException.Collector();
        for (int i = 0; i < dates.size(); i++) {
            LocalDate d = dates.get(i);
            errors.check(d.isAfter(today), "dates[" + i + "]", "doit être un jour à venir");
            errors.check(CalendarRules.isWorkingDay(d), "dates[" + i + "]", "doit être un jour ouvré");
        }
        errors.throwIfAny();
        for (LocalDate d : dates) {
            SpotRelease.Key key = new SpotRelease.Key(s.getId(), d);
            if (!releases.existsById(key)) {
                SpotRelease r = new SpotRelease();
                r.setId(key);
                r.setCreatedAt(clock.instant());
                releases.save(r);
            }
        }
        releases.flush();
        return me(me);
    }

    /** Le titulaire reprend sa place, tant que personne ne l'a réservée ce jour-là. */
    @Transactional
    public MeDto takeBack(AuthUser me, LocalDate day) {
        Spot s = spots.findByHolderId(me.id()).orElseThrow(() -> ApiException.forbidden("Vous n'êtes titulaire d'aucune place"));
        spots.lock(s.getId());
        SpotRelease.Key key = new SpotRelease.Key(s.getId(), day);
        if (!releases.existsById(key)) {
            throw ApiException.notFound("Libération");
        }
        if (bookings.findBySpotIdAndDay(s.getId(), day).isPresent()) {
            throw ApiException.conflict("SPOT_ALREADY_TAKEN", "Un collègue a déjà réservé votre place ce jour-là");
        }
        releases.deleteById(key);
        return me(me);
    }

    // ---------- réservations ----------

    /** Sous verrou des places : deux réservations simultanées de la même place, une seule réussit. */
    @Transactional
    public MyBookingDto book(AuthUser me, LocalDate day, UUID spotId) {
        spots.lockAll();
        LocalDate today = LocalDate.now(clock);
        if (day.isBefore(today)) {
            throw ValidationException.field("day", "ne doit pas être dans le passé");
        }
        if (!CalendarRules.isWorkingDay(day)) {
            throw ValidationException.field("day", "doit être un jour ouvré");
        }
        if (day.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw ApiException.conflict("BOOKING_TOO_EARLY", "Réservation au plus " + MAX_DAYS_AHEAD + " jours à l'avance");
        }
        if (bookings.findByUserIdAndDay(me.id(), day).isPresent()) {
            throw ApiException.conflict("ALREADY_BOOKED_TODAY", "Vous avez déjà une place ce jour-là");
        }
        Optional<Spot> own = spots.findByHolderId(me.id());
        if (own.isPresent() && !releases.existsById(new SpotRelease.Key(own.get().getId(), day))) {
            throw ApiException.conflict("HOLDER_HAS_SPOT", "Vous avez déjà votre propre place ce jour-là");
        }
        List<SpotDayDto> free = day(me, day).spots().stream()
                .filter(s -> s.state().equals("FREE") || s.state().equals("RELEASED")).toList();
        SpotDayDto chosen = spotId == null ? free.stream().findFirst().orElseThrow(() ->
                ApiException.conflict("SPOT_ALREADY_TAKEN", "Plus aucune place libre ce jour-là"))
                : free.stream().filter(s -> s.spotId().equals(spotId)).findFirst().orElseThrow(() -> {
                    spots.findById(spotId).orElseThrow(() -> ApiException.notFound("Place"));
                    return ApiException.conflict("SPOT_ALREADY_TAKEN", "Cette place n'est pas libre ce jour-là");
                });
        SpotBooking b = new SpotBooking();
        b.setSpotId(chosen.spotId());
        b.setUserId(me.id());
        b.setDay(day);
        bookings.saveAndFlush(b);
        return new MyBookingDto(b.getId(), day, chosen.number(), chosen.level());
    }

    @Transactional
    public void cancel(AuthUser me, LocalDate day) {
        SpotBooking b = bookings.findByUserIdAndDay(me.id(), day).orElseThrow(() -> ApiException.notFound("Réservation"));
        bookings.delete(b);
    }

    // ---------- administration ----------

    @Transactional(readOnly = true)
    public List<SpotDto> list(AuthUser me) {
        me.requireAdmin();
        return spots.findAllByOrderByNumberAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public SpotDto create(AuthUser me, SpotRequest req) {
        me.requireAdmin();
        if (spots.existsByNumberIgnoreCase(req.number())) {
            throw ApiException.conflict("SPOT_EXISTS", "Ce numéro de place existe déjà");
        }
        Spot s = new Spot();
        s.setNumber(req.number().toUpperCase());
        s.setLevel(req.level().trim());
        return toDto(spots.saveAndFlush(s));
    }

    @Transactional
    public SpotDto setHolder(AuthUser me, UUID id, UUID holderId) {
        me.requireAdmin();
        Spot s = spots.findById(id).orElseThrow(() -> ApiException.notFound("Place"));
        if (holderId != null) {
            userLookup.require(holderId);
            spots.findByHolderId(holderId).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
                throw ApiException.conflict("HOLDER_ALREADY_HAS_SPOT", "Cette personne est déjà titulaire de la place " + o.getNumber());
            });
        }
        s.setHolderId(holderId);
        // les libérations à venir de l'ancien titulaire n'ont plus de sens
        releases.findByIdSpotIdAndIdDayGreaterThanEqualOrderByIdDayAsc(id, LocalDate.now(clock)).stream()
                .filter(r -> bookings.findBySpotIdAndDay(id, r.getId().getDay()).isEmpty()).forEach(releases::delete);
        return toDto(spots.saveAndFlush(s));
    }

    /** Taux d'utilisation : jours ouvrés où la place est occupée (titulaire présent ou réservation). */
    @Transactional(readOnly = true)
    public List<UsageDto> usage(AuthUser me, YearMonth month) {
        me.requireAdmin();
        YearMonth m = month == null ? YearMonth.now(clock) : month;
        List<LocalDate> days = m.atDay(1).datesUntil(m.atEndOfMonth().plusDays(1)).filter(CalendarRules::isWorkingDay).toList();
        Set<String> released = releases.findByIdDayBetween(m.atDay(1), m.atEndOfMonth()).stream()
                .map(r -> r.getId().getSpotId() + "|" + r.getId().getDay()).collect(Collectors.toSet());
        Set<String> booked = bookings.findByDayBetween(m.atDay(1), m.atEndOfMonth()).stream()
                .map(b -> b.getSpotId() + "|" + b.getDay()).collect(Collectors.toSet());
        return spots.findAllByOrderByNumberAsc().stream().map(s -> {
            long used = days.stream().filter(d -> {
                String key = s.getId() + "|" + d;
                return booked.contains(key) || (s.getHolderId() != null && !released.contains(key));
            }).count();
            return new UsageDto(s.getId(), s.getNumber(), used, days.size(), days.isEmpty() ? 0 : (int) Math.round(used * 100.0 / days.size()));
        }).toList();
    }

    private SpotDto toDto(Spot s) {
        return new SpotDto(s.getId(), s.getNumber(), s.getLevel(), s.getHolderId(), userLookup.name(s.getHolderId()));
    }
}
