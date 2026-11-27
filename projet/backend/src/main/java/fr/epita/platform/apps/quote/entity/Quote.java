package fr.epita.platform.apps.quote.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotes")
@Getter
@Setter
@NoArgsConstructor
public class Quote extends BaseEntity {

    private String number;
    @Column(name = "sales_id", nullable = false)
    private UUID salesId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(nullable = false)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status = QuoteStatus.DRAFT;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "refusal_comment")
    private String refusalComment;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuoteLine> lines = new ArrayList<>();

    /** Statut affiché : un devis envoyé dont la validité est dépassée est expiré. */
    public QuoteStatus effectiveStatus(LocalDate today) {
        if (status == QuoteStatus.SENT && validUntil != null && validUntil.isBefore(today)) {
            return QuoteStatus.EXPIRED;
        }
        return status;
    }
}
