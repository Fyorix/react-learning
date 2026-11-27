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
@Table(name = "grouporder_options")
@Getter
@Setter
@NoArgsConstructor
public class ItemOption extends BaseEntity {

    @Column(name = "item_id", nullable = false)
    private UUID itemId;
    @Column(nullable = false)
    private String label;
    @Column(name = "extra_price", nullable = false)
    private BigDecimal extraPrice;
}
