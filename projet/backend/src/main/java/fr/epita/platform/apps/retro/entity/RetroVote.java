package fr.epita.platform.apps.retro.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Votes d'un participant sur une carte ou un groupe (1 ou 2). */
@Entity
@Table(name = "retro_votes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetroVote {

    @EmbeddedId
    private Key id;
    @Column(nullable = false)
    private int count;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "retro_id")
        private UUID retroId;
        @Column(name = "user_id")
        private UUID userId;
        @Column(name = "target_id")
        private UUID targetId;
    }
}
