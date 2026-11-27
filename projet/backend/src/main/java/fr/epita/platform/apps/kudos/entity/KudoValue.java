package fr.epita.platform.apps.kudos.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kudos_values")
@Getter
@Setter
@NoArgsConstructor
public class KudoValue extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
}
