package fr.epita.platform.apps.supply.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supply_requests")
@Getter
@Setter
@NoArgsConstructor
public class SupplyRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;
    private String reason;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
