package fr.epita.platform.apps.farewell.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "farewell_contributions")
@Getter
@Setter
@NoArgsConstructor
public class Contribution extends BaseEntity {

    @Column(name = "pot_id", nullable = false)
    private UUID potId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private BigDecimal amount;
    private String message;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
