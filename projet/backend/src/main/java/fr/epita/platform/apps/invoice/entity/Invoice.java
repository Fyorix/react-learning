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
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends BaseEntity {

    private String number;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(name = "issued_at")
    private Instant issuedAt;
    @Column(name = "total_ht", nullable = false)
    private BigDecimal totalHt;
    @Column(name = "total_vat", nullable = false)
    private BigDecimal totalVat;
    @Column(name = "total_ttc", nullable = false)
    private BigDecimal totalTtc;
    @Column(name = "created_by")
    private UUID createdBy;
}
