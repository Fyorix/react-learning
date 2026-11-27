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
@Table(name = "crm_tasks")
@Getter
@Setter
@NoArgsConstructor
public class FollowUpTask extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column(name = "account_id")
    private UUID accountId;
    @Column(name = "opportunity_id")
    private UUID opportunityId;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(nullable = false)
    private String label;
    @Column(nullable = false)
    private boolean done;
}
