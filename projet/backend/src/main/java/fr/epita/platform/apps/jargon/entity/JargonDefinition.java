package fr.epita.platform.apps.jargon.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "jargon_definitions")
@Getter
@Setter
@NoArgsConstructor
public class JargonDefinition extends BaseEntity {

    @Column(name = "term_id", nullable = false)
    private UUID termId;
    @Column(nullable = false)
    private String text;
    private String example;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContributionStatus status;
    @Column(name = "author_id")
    private UUID authorId;
    private String reason;
}
