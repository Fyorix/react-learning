package fr.epita.platform.apps.supply.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supply_items")
@Getter
@Setter
@NoArgsConstructor
public class SupplyItem extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private String unit;
    @Column(nullable = false)
    private int stock;
    @Column(nullable = false)
    private int reserved;
    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold;
    @Column(name = "monthly_quota")
    private Integer monthlyQuota;
    @Column(nullable = false)
    private boolean active = true;

    public int available() {
        return stock - reserved;
    }
}
