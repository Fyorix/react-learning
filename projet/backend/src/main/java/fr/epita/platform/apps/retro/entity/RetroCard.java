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
@Table(name = "retro_cards")
@Getter
@Setter
@NoArgsConstructor
public class RetroCard extends BaseEntity {

    @Column(name = "retro_id", nullable = false)
    private UUID retroId;
    @Column(name = "author_id", nullable = false)
    private UUID authorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "column_name", nullable = false)
    private CardColumn column;
    @Column(nullable = false)
    private String text;
    @Column(name = "group_id")
    private UUID groupId;
}
