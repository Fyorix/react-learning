package fr.epita.platform.apps.kb.entity;

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

@Entity
@Table(name = "kb_feedback")
@Getter
@Setter
@NoArgsConstructor
public class KbFeedback {

    @EmbeddedId
    private Key id;
    @Column(nullable = false)
    private boolean helpful;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "article_id")
        private UUID articleId;
        @Column(name = "user_id")
        private UUID userId;
    }
}
