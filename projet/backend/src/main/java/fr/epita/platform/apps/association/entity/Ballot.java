package fr.epita.platform.apps.association.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Émargement : l'adhérent a voté sur la résolution (sans son choix). */
@Entity
@Table(name = "association_ballots")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Ballot {

    @EmbeddedId
    private Key id;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "resolution_id")
        private UUID resolutionId;
        @Column(name = "user_id")
        private UUID userId;
    }
}
