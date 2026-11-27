package fr.epita.platform.apps.enps.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "enps_team_members")
@Getter
@Setter
@NoArgsConstructor
public class TeamMember {

    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "team_id", nullable = false)
    private UUID teamId;
}
