package fr.epita.platform.apps.association.entity;

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
@Table(name = "association_activities")
@Getter
@Setter
@NoArgsConstructor
public class AssociationActivity extends BaseEntity {

    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private Instant date;
    @Column(nullable = false)
    private int capacity;
    @Column(name = "members_only", nullable = false)
    private boolean membersOnly;
}
