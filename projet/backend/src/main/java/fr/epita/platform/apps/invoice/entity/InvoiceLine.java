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
@Table(name = "invoice_lines")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLine extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false)
    private String label;
    @Column(nullable = false)
    private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;
    @Column(name = "vat_rate", nullable = false)
    private BigDecimal vatRate;
}
