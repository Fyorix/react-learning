package fr.epita.platform.apps.kanban.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "kanban_boards")
@Getter
@Setter
@NoArgsConstructor
public class Board extends BaseEntity {

    @Column(nullable = false)
    private String name;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kanban_board_members", joinColumns = @JoinColumn(name = "board_id"))
    @Column(name = "user_id")
    private Set<UUID> memberIds = new HashSet<>();

    public boolean isMember(UUID userId) {
        return ownerId.equals(userId) || memberIds.contains(userId);
    }
}
