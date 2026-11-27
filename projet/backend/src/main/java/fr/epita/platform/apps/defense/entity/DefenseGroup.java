package fr.epita.platform.apps.defense.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "defense_groups")
@Getter
@Setter
@NoArgsConstructor
public class DefenseGroup extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    @Column(name = "supervisor_id", nullable = false)
    private UUID supervisorId;
    @Column(name = "slot_id", unique = true)
    private UUID slotId;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "defense_group_students", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "student_id")
    private Set<UUID> studentIds = new HashSet<>();
}
