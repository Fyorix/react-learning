package fr.epita.platform.apps.birthday.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "birthday_teams")
@Getter
@Setter
@NoArgsConstructor
public class BirthdayTeam extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
}
