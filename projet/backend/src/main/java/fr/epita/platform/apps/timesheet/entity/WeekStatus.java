package fr.epita.platform.apps.timesheet.entity;

public enum WeekStatus {
    DRAFT, SUBMITTED, APPROVED, RETURNED;

    public boolean editable() {
        return this == DRAFT || this == RETURNED;
    }
}
