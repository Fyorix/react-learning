package fr.epita.platform.apps.vehicle.entity;

public final class VehicleEnums {

    private VehicleEnums() {
    }

    public enum VehicleStatus { AVAILABLE, MAINTENANCE, RETIRED }

    /** BOOKED = réservé, OUT = parti (départ enregistré), RETURNED = rendu, CANCELLED = annulé */
    public enum BookingStatus { BOOKED, OUT, RETURNED, CANCELLED }

    public enum Severity { MINOR, BLOCKING }
}
