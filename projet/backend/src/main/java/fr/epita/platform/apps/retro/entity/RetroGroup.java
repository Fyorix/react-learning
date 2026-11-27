package fr.epita.platform.apps.retro.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "retro_groups")
@Getter
@Setter
@NoArgsConstructor
public class RetroGroup extends BaseEntity {

    @Column(name = "retro_id", nullable = false)
    private UUID retroId;
    @Column(nullable = false)
    private String title;
}
