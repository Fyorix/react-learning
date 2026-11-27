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
@Table(name = "oncall_teams")
@Getter
@Setter
@NoArgsConstructor
public class OnCallTeam extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "oncall_members", joinColumns = @JoinColumn(name = "team_id"))
    @Column(name = "user_id")
    private Set<UUID> memberIds = new HashSet<>();
}
