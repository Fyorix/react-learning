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
@Table(name = "grouporder_suppliers")
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    @Column(name = "minimum_order", nullable = false)
    private BigDecimal minimumOrder;
    @Column(name = "delivery_fee", nullable = false)
    private BigDecimal deliveryFee;
}
