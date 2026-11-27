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
@Table(name = "retro_teams")
@Getter
@Setter
@NoArgsConstructor
public class RetroTeam extends BaseEntity {

    @Column(nullable = false)
    private String name;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "retro_team_members", joinColumns = @JoinColumn(name = "team_id"))
    @Column(name = "user_id")
    private Set<UUID> memberIds = new HashSet<>();
}
