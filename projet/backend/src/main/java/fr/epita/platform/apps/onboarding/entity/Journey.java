package fr.epita.platform.apps.onboarding.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "onboarding_journeys")
@Getter
@Setter
@NoArgsConstructor
public class Journey extends BaseEntity {

    @Column(name = "template_name", nullable = false)
    private String templateName;
    @Column(name = "newcomer_id", nullable = false)
    private UUID newcomerId;
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;
    @Column(name = "buddy_id", nullable = false)
    private UUID buddyId;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @OneToMany(mappedBy = "journey", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dueDate ASC, position ASC")
    private List<JourneyTask> tasks = new ArrayList<>();

    /** Terminé quand toutes les tâches obligatoires sont faites. */
    public boolean isCompleted() {
        return tasks.stream().filter(JourneyTask::isMandatory).allMatch(JourneyTask::isDone);
    }
}
