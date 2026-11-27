package fr.epita.platform.apps.supply.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supply_lines")
@Getter
@Setter
@NoArgsConstructor
public class SupplyLine extends BaseEntity {

    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(name = "item_id", nullable = false)
    private UUID itemId;
    @Column(nullable = false)
    private int quantity;
}
