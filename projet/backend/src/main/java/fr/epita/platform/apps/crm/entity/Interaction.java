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
@Table(name = "crm_interactions")
@Getter
@Setter
@NoArgsConstructor
public class Interaction extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    @Column(name = "opportunity_id")
    private UUID opportunityId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InteractionType type;
    @Column(nullable = false)
    private LocalDate date;
    @Column(nullable = false)
    private String summary;
    @Column(name = "author_id")
    private UUID authorId;
}
