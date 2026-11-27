package fr.epita.platform.apps.form.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "form_responses")
@Getter
@Setter
@NoArgsConstructor
public class FormResponse extends BaseEntity {

    @Column(name = "form_id", nullable = false)
    private UUID formId;
    @Column(nullable = false)
    private int version;
    /** Null pour un formulaire anonyme. */
    @Column(name = "user_id")
    private UUID userId;
    /** Réponses validées, en JSON {fieldId: valeur}. */
    @Column(nullable = false)
    private String answers;
}
