package fr.epita.platform.apps.helpdesk.entity;

public final class HelpdeskEnums {

    private HelpdeskEnums() {
    }

    public enum Priority { CRITICAL, HIGH, NORMAL, LOW }

    public enum Category { HARDWARE, SOFTWARE, NETWORK, ACCESS, OTHER }

    public enum TicketStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }
}
