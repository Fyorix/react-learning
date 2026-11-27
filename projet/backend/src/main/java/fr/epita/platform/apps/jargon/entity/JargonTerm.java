package fr.epita.platform.apps.jargon.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "jargon_terms")
@Getter
@Setter
@NoArgsConstructor
public class JargonTerm extends BaseEntity {

    @Column(nullable = false)
    private String term;
    /** Forme sans casse ni accents, pour l'unicité et la recherche. */
    @Column(nullable = false)
    private String normalized;
    private String expansion;
    private String team;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContributionStatus status;
    @Column(name = "author_id")
    private UUID authorId;
    private String reason;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "jargon_term_links", joinColumns = @JoinColumn(name = "term_id"))
    @Column(name = "related_id")
    private Set<UUID> relatedIds = new HashSet<>();
}
