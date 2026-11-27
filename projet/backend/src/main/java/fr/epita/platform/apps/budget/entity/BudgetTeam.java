package fr.epita.platform.apps.budget.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "budget_teams")
@Getter
@Setter
@NoArgsConstructor
public class BudgetTeam extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "budget_team_members", joinColumns = @JoinColumn(name = "team_id"))
    @Column(name = "user_id")
    private Set<UUID> memberIds = new HashSet<>();
}
