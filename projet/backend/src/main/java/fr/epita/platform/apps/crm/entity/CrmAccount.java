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
@Table(name = "crm_accounts")
@Getter
@Setter
@NoArgsConstructor
public class CrmAccount extends BaseEntity {

    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String industry;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountSize size;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
}
