package fr.epita.platform.apps.training.entity;

public enum EnrollmentStatus {
    REQUESTED, CONFIRMED, WAITLISTED, REJECTED, CANCELLED;

    /** Inscription qui occupe ou attend une place. */
    public boolean active() {
        return this == REQUESTED || this == CONFIRMED || this == WAITLISTED;
    }
}
