package fr.epita.platform.apps.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "goal_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoalReport {

    @Id
    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;
}
