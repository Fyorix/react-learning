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
@Table(name = "grouporder_lines")
@Getter
@Setter
@NoArgsConstructor
public class OrderLine extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "item_id", nullable = false)
    private UUID itemId;
    @Column(nullable = false)
    private String label;
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private int quantity;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "grouporder_line_options", joinColumns = @JoinColumn(name = "line_id"))
    @Column(name = "option_id")
    private List<UUID> optionIds = new ArrayList<>();
}
