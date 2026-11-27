package fr.epita.platform.apps.classified.entity;

import fr.epita.platform.apps.classified.entity.ClassifiedEnums.ListingStatus;
import fr.epita.platform.apps.classified.entity.ClassifiedEnums.ListingType;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "classifieds")
@Getter
@Setter
@NoArgsConstructor
public class Listing extends BaseEntity {

    @Column(name = "author_id", nullable = false)
    private UUID authorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingType type;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private String category;
    private BigDecimal price;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status = ListingStatus.ACTIVE;
    @Column(nullable = false)
    private boolean hidden;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Photo> photos = new ArrayList<>();

    public ListingStatus effectiveStatus(Instant now) {
        if (status != ListingStatus.CLOSED && expiresAt.isBefore(now)) {
            return ListingStatus.EXPIRED;
        }
        return status;
    }

    public boolean isLive(Instant now) {
        ListingStatus s = effectiveStatus(now);
        return s == ListingStatus.ACTIVE || s == ListingStatus.RESERVED;
    }
}
