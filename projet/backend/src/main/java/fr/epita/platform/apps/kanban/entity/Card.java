package fr.epita.platform.apps.kanban.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "kanban_cards")
@Getter
@Setter
@NoArgsConstructor
public class Card extends BaseEntity {

    @Column(name = "column_id", nullable = false)
    private UUID columnId;
    @Column(nullable = false)
    private String title;
    private String description;
    @Column(name = "assignee_id")
    private UUID assigneeId;
    @Column(name = "due_date")
    private LocalDate dueDate;
    /** Étiquettes séparées par des virgules. */
    @Column(nullable = false)
    private String labels = "";
    @Column(nullable = false)
    private int position;

    public List<String> labelList() {
        return labels.isBlank() ? List.of() : Arrays.asList(labels.split(","));
    }
}
