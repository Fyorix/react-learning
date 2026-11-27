package fr.epita.platform.apps.invoice.entity;

/** Statut calculé (jamais stocké) à partir de l'émission, des paiements, des avoirs et de l'échéance. */
public enum InvoiceStatus {
    DRAFT, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED
}
