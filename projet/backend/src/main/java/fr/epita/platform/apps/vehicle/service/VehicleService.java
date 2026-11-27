package fr.epita.platform.apps.vehicle.service;

import fr.epita.platform.apps.vehicle.dto.VehicleDtos.BookingDto;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.BookingRequest;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.CheckinRequest;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.IssueDto;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.MaintenanceDto;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.VehicleDto;
import fr.epita.platform.apps.vehicle.dto.VehicleDtos.VehicleRequest;
import fr.epita.platform.apps.vehicle.entity.Vehicle;
import fr.epita.platform.apps.vehicle.entity.VehicleBooking;
import fr.epita.platform.apps.vehicle.entity.VehicleEnums.BookingStatus;
import fr.epita.platform.apps.vehicle.entity.VehicleEnums.Severity;
import fr.epita.platform.apps.vehicle.entity.VehicleEnums.VehicleStatus;
import fr.epita.platform.apps.vehicle.entity.VehicleIssue;
import fr.epita.platform.apps.vehicle.repository.VehicleBookingRepository;
import fr.epita.platform.apps.vehicle.repository.VehicleIssueRepository;
import fr.epita.platform.apps.vehicle.repository.VehicleRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleService {

    static final Duration MAX_BOOKING = Duration.ofDays(5);

    private final VehicleRepository vehicles;
    private final VehicleBookingRepository bookings;
    private final VehicleIssueRepository issues;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- flotte ----------

    /** Avec une période : uniquement les véhicules AVAILABLE sans réservation qui la chevauche. */
    @Transactional(readOnly = true)
    public List<VehicleDto> list(Instant start, Instant end) {
        if ((start == null) != (end == null)) {
            throw ValidationException.field("end", "start et end vont ensemble");
        }
        return vehicles.findAllByOrderByModelAsc().stream()
                .filter(v -> start == null || (v.getStatus() == VehicleStatus.AVAILABLE && !overlaps(v.getId(), start, end, null)))
                .map(VehicleService::toDto).toList();
    }

    @Transactional
    public VehicleDto create(AuthUser me, VehicleRequest req) {
        me.requireAdmin();
        if (vehicles.existsByPlateIgnoreCase(req.plate())) {
            throw ApiException.conflict("PLATE_EXISTS", "Cette immatriculation existe déjà");
        }
        Vehicle v = new Vehicle();
        apply(v, req);
        v.setMileage(req.mileage());
        v.setLastServiceMileage(req.mileage());
        return toDto(vehicles.saveAndFlush(v));
    }

    @Transactional
    public VehicleDto update(AuthUser me, UUID id, VehicleRequest req) {
        me.requireAdmin();
        Vehicle v = vehicle(id);
        if (vehicles.existsByPlateIgnoreCaseAndIdNot(req.plate(), id)) {
            throw ApiException.conflict("PLATE_EXISTS", "Cette immatriculation existe déjà");
        }
        if (req.mileage() < v.getMileage()) {
            throw ValidationException.field("mileage", "ne peut pas diminuer (" + v.getMileage() + " km actuellement)");
        }
        VehicleStatus before = v.getStatus();
        apply(v, req);
        v.setMileage(req.mileage());
        if (before == VehicleStatus.AVAILABLE && v.getStatus() != VehicleStatus.AVAILABLE) {
            cancelFuture(v.getId());
        }
        return toDto(v);
    }

    // ---------- réservations ----------

    @Transactional(readOnly = true)
    public List<BookingDto> planning(AuthUser me, UUID vehicleId, Instant from, Instant to) {
        vehicle(vehicleId);
        return bookings.findByVehicleIdOrderByStartsAtAsc(vehicleId).stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .filter(b -> from == null || b.getEndsAt().isAfter(from))
                .filter(b -> to == null || b.getStartsAt().isBefore(to))
                .map(b -> toDto(b, me)).toList();
    }

    /** Verrou du véhicule : deux réservations qui se chevauchent ne peuvent pas réussir toutes les deux. */
    @Transactional
    public BookingDto book(AuthUser me, UUID vehicleId, BookingRequest raw) {
        // bornes à la minute : PostgreSQL arrondit à la microseconde, deux créneaux bout à bout ne doivent pas se chevaucher
        BookingRequest req = new BookingRequest(raw.start().truncatedTo(ChronoUnit.MINUTES), raw.end().truncatedTo(ChronoUnit.MINUTES),
                raw.purpose(), raw.destination());
        Vehicle v = vehicles.lock(vehicleId).orElseThrow(() -> ApiException.notFound("Véhicule"));
        var errors = new ValidationException.Collector();
        errors.check(req.start().isAfter(clock.instant()), "start", "doit être dans le futur");
        errors.check(req.end().isAfter(req.start()), "end", "doit être postérieure au début");
        errors.check(Duration.between(req.start(), req.end()).compareTo(MAX_BOOKING) <= 0, "end", "5 jours maximum");
        errors.throwIfAny();
        if (v.getStatus() != VehicleStatus.AVAILABLE) {
            throw ApiException.conflict("VEHICLE_UNAVAILABLE", "Ce véhicule n'est pas réservable (" + v.getStatus() + ")");
        }
        if (overlaps(vehicleId, req.start(), req.end(), null)) {
            throw ApiException.conflict("VEHICLE_ALREADY_BOOKED", "Le véhicule est déjà réservé sur cette période");
        }
        VehicleBooking b = new VehicleBooking();
        b.setVehicleId(vehicleId);
        b.setUserId(me.id());
        b.setStartsAt(req.start());
        b.setEndsAt(req.end());
        b.setPurpose(req.purpose().trim());
        b.setDestination(req.destination().trim());
        return toDto(bookings.saveAndFlush(b), me);
    }

    @Transactional
    public void cancel(AuthUser me, UUID bookingId) {
        VehicleBooking b = booking(bookingId);
        me.requireOwnerOrAdmin(b.getUserId());
        if (b.getStatus() != BookingStatus.BOOKED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seule une réservation pas encore partie peut être annulée");
        }
        b.setStatus(BookingStatus.CANCELLED);
    }

    @Transactional
    public BookingDto checkout(AuthUser me, UUID bookingId, int mileage) {
        VehicleBooking b = booking(bookingId);
        me.requireOwnerOrAdmin(b.getUserId());
        if (b.getStatus() != BookingStatus.BOOKED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Le départ est déjà enregistré ou la réservation est annulée");
        }
        Vehicle v = vehicles.lock(b.getVehicleId()).orElseThrow();
        if (mileage < v.getMileage()) {
            throw ValidationException.field("mileage", "doit être au moins égal au kilométrage connu (" + v.getMileage() + " km)");
        }
        v.setMileage(mileage);
        b.setStartMileage(mileage);
        b.setStatus(BookingStatus.OUT);
        return toDto(b, me);
    }

    /** Retour : met à jour le kilométrage ; un problème bloquant passe le véhicule en entretien et annule les réservations futures. */
    @Transactional
    public BookingDto checkin(AuthUser me, UUID bookingId, CheckinRequest req) {
        VehicleBooking b = booking(bookingId);
        me.requireOwnerOrAdmin(b.getUserId());
        if (b.getStatus() != BookingStatus.OUT) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Le départ n'a pas été enregistré");
        }
        if (req.mileage() < b.getStartMileage()) {
            throw ValidationException.field("mileage", "doit être au moins égal au kilométrage de départ (" + b.getStartMileage() + " km)");
        }
        Vehicle v = vehicles.lock(b.getVehicleId()).orElseThrow();
        v.setMileage(Math.max(v.getMileage(), req.mileage()));
        b.setEndMileage(req.mileage());
        b.setStatus(BookingStatus.RETURNED);
        if (req.issue() != null) {
            VehicleIssue issue = new VehicleIssue();
            issue.setVehicleId(v.getId());
            issue.setBookingId(b.getId());
            issue.setSeverity(req.issue().severity());
            issue.setDescription(req.issue().description().trim());
            issues.save(issue);
            if (req.issue().severity() == Severity.BLOCKING) {
                v.setStatus(VehicleStatus.MAINTENANCE);
                cancelFuture(v.getId());
            }
        }
        return toDto(b, me);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> mine(AuthUser me) {
        return bookings.findByUserIdOrderByStartsAtDesc(me.id()).stream().map(b -> toDto(b, me)).toList();
    }

    // ---------- entretien ----------

    @Transactional(readOnly = true)
    public MaintenanceDto maintenance(AuthUser me) {
        me.requireAdmin();
        List<VehicleDto> due = vehicles.findAllByOrderByModelAsc().stream()
                .filter(v -> v.getStatus() != VehicleStatus.RETIRED && v.serviceDue()).map(VehicleService::toDto).toList();
        List<IssueDto> open = issues.findByResolvedFalseOrderByCreatedAtDesc().stream().map(i -> {
            Vehicle v = vehicle(i.getVehicleId());
            return new IssueDto(i.getId(), v.getId(), label(v), i.getSeverity(), i.getDescription(), i.getCreatedAt());
        }).toList();
        return new MaintenanceDto(due, open);
    }

    /** Entretien : remet le compteur d'entretien à zéro, clôt les problèmes et rend le véhicule disponible. */
    @Transactional
    public VehicleDto service(AuthUser me, UUID id) {
        me.requireAdmin();
        Vehicle v = vehicle(id);
        if (v.getStatus() == VehicleStatus.RETIRED) {
            throw ApiException.conflict("VEHICLE_UNAVAILABLE", "Véhicule retiré du parc");
        }
        v.setLastServiceMileage(v.getMileage());
        v.setStatus(VehicleStatus.AVAILABLE);
        issues.findByVehicleIdAndResolvedFalse(id).forEach(i -> i.setResolved(true));
        return toDto(v);
    }

    // ---------- outils ----------

    private boolean overlaps(UUID vehicleId, Instant start, Instant end, UUID excluded) {
        return bookings.findByVehicleIdOrderByStartsAtAsc(vehicleId).stream()
                .filter(VehicleBooking::blocksVehicle)
                .filter(b -> !b.getId().equals(excluded))
                .anyMatch(b -> b.getStartsAt().isBefore(end) && b.getEndsAt().isAfter(start));
    }

    private void cancelFuture(UUID vehicleId) {
        Instant now = clock.instant();
        bookings.findByVehicleIdOrderByStartsAtAsc(vehicleId).stream()
                .filter(b -> b.getStatus() == BookingStatus.BOOKED && b.getStartsAt().isAfter(now))
                .forEach(b -> b.setStatus(BookingStatus.CANCELLED));
    }

    private static void apply(Vehicle v, VehicleRequest req) {
        v.setPlate(req.plate());
        v.setModel(req.model().trim());
        v.setSeats(req.seats());
        v.setLicenseType(req.licenseType());
        v.setStatus(req.status() == null ? VehicleStatus.AVAILABLE : req.status());
    }

    private Vehicle vehicle(UUID id) {
        return vehicles.findById(id).orElseThrow(() -> ApiException.notFound("Véhicule"));
    }

    private VehicleBooking booking(UUID id) {
        return bookings.findById(id).orElseThrow(() -> ApiException.notFound("Réservation"));
    }

    private static String label(Vehicle v) {
        return v.getModel() + " (" + v.getPlate() + ")";
    }

    private static VehicleDto toDto(Vehicle v) {
        return new VehicleDto(v.getId(), v.getPlate(), v.getModel(), v.getSeats(), v.getLicenseType(), v.getStatus(),
                v.getMileage(), v.getMileage() - v.getLastServiceMileage(), v.serviceDue());
    }

    private BookingDto toDto(VehicleBooking b, AuthUser me) {
        Vehicle v = vehicle(b.getVehicleId());
        return new BookingDto(b.getId(), v.getId(), label(v), b.getUserId(), userLookup.name(b.getUserId()), b.getStartsAt(),
                b.getEndsAt(), b.getPurpose(), b.getDestination(), b.getStatus(), b.getStartMileage(), b.getEndMileage(),
                b.getUserId().equals(me.id()));
    }
}
