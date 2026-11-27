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
@Table(name = "invoice_payments")
@Getter
@Setter
@NoArgsConstructor
public class InvoicePayment extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;
    @Column(nullable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;
    @Column(name = "created_by")
    private UUID createdBy;
}
