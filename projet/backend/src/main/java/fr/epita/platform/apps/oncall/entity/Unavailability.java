package fr.epita.platform.apps.oncall.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "oncall_unavailabilities")
@Getter
@Setter
@NoArgsConstructor
public class Unavailability extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;
    @Column(nullable = false)
    private String reason;
}
