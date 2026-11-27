package fr.epita.platform.apps.crm.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "crm_opportunities")
@Getter
@Setter
@NoArgsConstructor
public class Opportunity extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(name = "expected_close_date", nullable = false)
    private LocalDate expectedCloseDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PipelineStage stage;
    @Column(name = "lost_reason")
    private String lostReason;
    @Column(name = "closed_at")
    private Instant closedAt;
}
