package fr.epita.platform.apps.kudos.entity;

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
@Table(name = "kudos_reactions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Reaction {

    @EmbeddedId
    private Key id;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "kudo_id")
        private UUID kudoId;
        @Column(name = "user_id")
        private UUID userId;
        @Column(name = "emoji")
        private String emoji;
    }
}
