package fr.epita.platform.apps.kanban.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "kanban_moves")
@Getter
@Setter
@NoArgsConstructor
public class CardMove extends BaseEntity {

    @Column(name = "card_id", nullable = false)
    private UUID cardId;
    @Column(name = "from_column_id")
    private UUID fromColumnId;
    @Column(name = "to_column_id", nullable = false)
    private UUID toColumnId;
    @Column(name = "from_name")
    private String fromName;
    @Column(name = "to_name", nullable = false)
    private String toName;
    @Column(name = "author_id")
    private UUID authorId;
}
