package fr.epita.platform.apps.enps.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enps_teams")
@Getter
@Setter
@NoArgsConstructor
public class EnpsTeam extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
}
