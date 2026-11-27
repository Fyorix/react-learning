package fr.epita.platform.apps.jargon.entity;

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

@Entity
@Table(name = "jargon_votes")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JargonVote {

    @EmbeddedId
    private Key id;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "definition_id")
        private UUID definitionId;
        @Column(name = "user_id")
        private UUID userId;
    }
}
