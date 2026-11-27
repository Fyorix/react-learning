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
@Table(name = "association_assemblies")
@Getter
@Setter
@NoArgsConstructor
public class Assembly extends BaseEntity {

    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private Instant date;
    @Column(name = "quorum_percent", nullable = false)
    private int quorumPercent;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssemblyStatus status;
    private Integer eligible;
    private Integer voters;
}
