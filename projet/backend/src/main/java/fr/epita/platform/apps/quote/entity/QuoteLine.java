package fr.epita.platform.apps.quote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Ligne de devis. Les champs copiés (référence, libellé, prix, TVA) sont figés à l'envoi. */
@Entity
@Table(name = "quote_lines")
@Getter
@Setter
@NoArgsConstructor
public class QuoteLine {

    @Id
    private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private BigDecimal discount;
    private String reference;
    private String label;
    @Column(name = "unit_price")
    private BigDecimal unitPrice;
    @Column(name = "vat_rate")
    private BigDecimal vatRate;

    public boolean isFrozen() {
        return unitPrice != null;
    }

    public String effectiveReference() {
        return isFrozen() ? reference : product.getReference();
    }

    public String effectiveLabel() {
        return isFrozen() ? label : product.getName();
    }

    public BigDecimal effectiveUnitPrice() {
        return isFrozen() ? unitPrice : product.getUnitPrice();
    }

    public BigDecimal effectiveVatRate() {
        return isFrozen() ? vatRate : product.getVatRate();
    }

    public void freeze() {
        reference = product.getReference();
        label = product.getName();
        unitPrice = product.getUnitPrice();
        vatRate = product.getVatRate();
    }
}
