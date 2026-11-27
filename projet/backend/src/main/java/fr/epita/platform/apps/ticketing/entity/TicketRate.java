package fr.epita.platform.apps.ticketing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ticketing_rates")
@Getter
@Setter
@NoArgsConstructor
public class TicketRate {

    @Id
    private UUID id;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false)
    private String label;
    @Column(nullable = false)
    private BigDecimal price;
    @Column(nullable = false)
    private int quota;
}
