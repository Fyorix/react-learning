package fr.epita.platform.apps.enps.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enps_surveys")
@Getter
@Setter
@NoArgsConstructor
public class Survey extends BaseEntity {

    @Column(nullable = false)
    private String title;
    @Column(name = "opens_at", nullable = false)
    private Instant opensAt;
    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;
}
