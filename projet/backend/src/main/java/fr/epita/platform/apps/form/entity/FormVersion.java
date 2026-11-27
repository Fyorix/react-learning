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
@Table(name = "form_versions")
@Getter
@Setter
@NoArgsConstructor
public class FormVersion extends BaseEntity {

    @Column(name = "form_id", nullable = false)
    private UUID formId;
    @Column(nullable = false)
    private int number;
    /** Liste des champs sérialisée en JSON. */
    @Column(nullable = false)
    private String fields;
}
