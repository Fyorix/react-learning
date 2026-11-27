package fr.epita.platform.apps.grade.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;



import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "grade_classes")
@Getter
@Setter
@NoArgsConstructor
public class SchoolClass extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "grade_class_students", joinColumns = @JoinColumn(name = "class_id"))
    @Column(name = "student_id")
    private Set<UUID> studentIds = new HashSet<>();
}
