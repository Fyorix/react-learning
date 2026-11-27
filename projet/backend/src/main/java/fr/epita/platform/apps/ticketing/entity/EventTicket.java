package fr.epita.platform.apps.ticketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticketing_tickets")
@Getter
@Setter
@NoArgsConstructor
public class EventTicket {

    @Id
    private UUID id;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "rate_id", nullable = false)
    private UUID rateId;
    /** UUID aléatoire + somme de contrôle : non devinable, vérifiable sans requête. */
    @Column(nullable = false, unique = true)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventTicketStatus status;
    @Column(name = "scanned_at")
    private Instant scannedAt;
    @Column(name = "scanned_by")
    private UUID scannedBy;
}
