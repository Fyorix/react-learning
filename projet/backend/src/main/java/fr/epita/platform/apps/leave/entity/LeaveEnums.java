package fr.epita.platform.apps.leave.entity;

public final class LeaveEnums {

    private LeaveEnums() {
    }

    public enum LeaveType { PAID, RTT, UNPAID }

    public enum Half { MORNING, AFTERNOON }

    public enum LeaveStatus { PENDING, APPROVED, REJECTED, CANCELLED }
}
