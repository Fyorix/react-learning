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
@Table(name = "kanban_columns")
@Getter
@Setter
@NoArgsConstructor
public class BoardColumn extends BaseEntity {

    @Column(name = "board_id", nullable = false)
    private UUID boardId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private int position;
    @Column(name = "wip_limit")
    private Integer wipLimit;
}
