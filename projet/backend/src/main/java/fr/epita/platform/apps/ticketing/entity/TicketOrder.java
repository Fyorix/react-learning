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
@Table(name = "ticketing_orders")
@Getter
@Setter
@NoArgsConstructor
public class TicketOrder extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketOrderStatus status;
    @Column(nullable = false)
    private BigDecimal total;
}
