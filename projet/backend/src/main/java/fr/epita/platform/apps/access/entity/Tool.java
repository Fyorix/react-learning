package fr.epita.platform.apps.access.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "access_tools")
@Getter
@Setter
@NoArgsConstructor
public class Tool extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    /** Niveaux proposés, séparés par des virgules. */
    @Column(nullable = false)
    private String levels;

    public List<AccessLevel> levelList() {
        return Arrays.stream(levels.split(",")).map(AccessLevel::valueOf).toList();
    }
}
