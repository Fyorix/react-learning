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
@Table(name = "retro_actions")
@Getter
@Setter
@NoArgsConstructor
public class RetroAction extends BaseEntity {

    @Column(name = "retro_id", nullable = false)
    private UUID retroId;
    @Column(name = "team_id", nullable = false)
    private UUID teamId;
    @Column(nullable = false)
    private String text;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(nullable = false)
    private boolean done;
}
