package fr.epita.platform.apps.invoice.entity;

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
@Table(name = "invoice_reminders")
@Getter
@Setter
@NoArgsConstructor
public class Reminder extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(nullable = false)
    private int level;
    @Column(name = "created_by")
    private UUID createdBy;
}
