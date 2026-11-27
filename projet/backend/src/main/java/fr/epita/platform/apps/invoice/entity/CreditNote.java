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
@Table(name = "invoice_credit_notes")
@Getter
@Setter
@NoArgsConstructor
public class CreditNote extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(nullable = false, unique = true)
    private String number;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String reason;
    @Column(name = "created_by")
    private UUID createdBy;
}
