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
@Table(name = "grouporder_items")
@Getter
@Setter
@NoArgsConstructor
public class MenuItem extends BaseEntity {

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private BigDecimal price;
    @Column(nullable = false)
    private boolean available;
    @Column(nullable = false)
    private boolean archived;
    @Column(nullable = false)
    private int position;
}
