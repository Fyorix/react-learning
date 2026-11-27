package fr.epita.platform.apps.directory.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "directory_skills")
@Getter
@Setter
@NoArgsConstructor
public class Skill extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    private String category;
}
