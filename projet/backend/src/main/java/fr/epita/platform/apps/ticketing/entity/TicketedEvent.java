package fr.epita.platform.apps.ticketing.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticketing_events")
@Getter
@Setter
@NoArgsConstructor
public class TicketedEvent extends BaseEntity {

    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String venue;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private int capacity;
    @Column(name = "sales_end_at", nullable = false)
    private Instant salesEndAt;
    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;
}
