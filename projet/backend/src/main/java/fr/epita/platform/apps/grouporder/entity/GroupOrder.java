package fr.epita.platform.apps.grouporder.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "grouporder_orders")
@Getter
@Setter
@NoArgsConstructor
public class GroupOrder extends BaseEntity {

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;
    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;
    @Column(nullable = false)
    private Instant deadline;
    private String note;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
}
