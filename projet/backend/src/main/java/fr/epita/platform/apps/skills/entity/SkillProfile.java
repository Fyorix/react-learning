package fr.epita.platform.apps.skills.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "skills_profiles")
@Getter
@Setter
@NoArgsConstructor
public class SkillProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(nullable = false)
    private int capacity = 3;

    /** compétences enseignées : compétence → niveau */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "skills_offers", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "skill_id")
    @Column(name = "level")
    private Map<UUID, Integer> offers = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "skills_wants", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "skill_id")
    private Set<UUID> wants = new HashSet<>();

    public SkillProfile(UUID userId) {
        this.userId = userId;
    }
}
