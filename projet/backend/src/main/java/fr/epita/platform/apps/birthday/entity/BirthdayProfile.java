package fr.epita.platform.apps.birthday.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "birthday_profiles")
@Getter
@Setter
@NoArgsConstructor
public class BirthdayProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "birth_date")
    private LocalDate birthDate;
    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;
    @Column(name = "team_id")
    private UUID teamId;
    @Column(name = "show_birthday", nullable = false)
    private boolean showBirthday = true;
}
