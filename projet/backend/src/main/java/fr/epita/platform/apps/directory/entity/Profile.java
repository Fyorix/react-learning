package fr.epita.platform.apps.directory.entity;

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
import java.util.Map;
import java.util.UUID;

/** Fiche d'annuaire : une par utilisateur, créée à la première lecture. */
@Entity
@Table(name = "directory_profiles")
@Getter
@Setter
@NoArgsConstructor
public class Profile {

    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "job_title", nullable = false)
    private String jobTitle = "";
    @Column(name = "team_id")
    private UUID teamId;
    @Column(name = "manager_id")
    private UUID managerId;
    @Column(nullable = false)
    private String location = "";
    private String phone;
    private String bio;
    @Column(name = "photo_key")
    private String photoKey;
    @Column(name = "photo_type")
    private String photoType;

    /** compétence → niveau (1 à 4) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "directory_profile_skills", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "skill_id")
    @Column(name = "level")
    private Map<UUID, Integer> skills = new HashMap<>();

    public Profile(UUID userId) {
        this.userId = userId;
    }
}
