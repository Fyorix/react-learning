package fr.epita.platform.apps.fitness.entity;

public enum FitnessBookingStatus {
    CONFIRMED,
    WAITLISTED,
    /** Annulation gratuite (plus de 2 h avant) ou cours annulé : crédit rendu. */
    CANCELLED,
    /** Annulation à moins de 2 h : crédit perdu. */
    LATE_CANCELLED
}
