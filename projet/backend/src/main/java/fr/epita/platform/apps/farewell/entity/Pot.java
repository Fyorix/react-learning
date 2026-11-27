package fr.epita.platform.apps.farewell.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "farewell_pots")
@Getter
@Setter
@NoArgsConstructor
public class Pot extends BaseEntity {

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;
    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Occasion occasion;
    @Column(nullable = false)
    private String title;
    private BigDecimal goal;
    @Column(name = "closes_at")
    private Instant closesAt;
    /** L'étape 3 a été enregistrée au moins une fois. */
    @Column(name = "invitees_set", nullable = false)
    private boolean inviteesSet;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PotStatus status;
    @Column(name = "closed_at")
    private Instant closedAt;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farewell_invitees", joinColumns = @JoinColumn(name = "pot_id"))
    @Column(name = "user_id")
    private Set<UUID> inviteeIds = new HashSet<>();
}
