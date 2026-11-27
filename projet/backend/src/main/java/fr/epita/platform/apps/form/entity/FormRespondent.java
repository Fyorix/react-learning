package fr.epita.platform.apps.form.entity;

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

/** Qui a répondu — sans lien avec la réponse (anonymat préservé). */
@Entity
@Table(name = "form_respondents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormRespondent {

    @EmbeddedId
    private Key id;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "form_id")
        private UUID formId;
        @Column(name = "user_id")
        private UUID userId;
    }
}
