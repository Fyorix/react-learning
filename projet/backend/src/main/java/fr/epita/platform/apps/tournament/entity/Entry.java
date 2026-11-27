package fr.epita.platform.apps.tournament.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournament_entries")
@Getter
@Setter
@NoArgsConstructor
public class Entry extends BaseEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;
    @Column(name = "player1_id", nullable = false)
    private UUID player1Id;
    @Column(name = "player2_id")
    private UUID player2Id;
    private Integer seed;

    public boolean has(UUID userId) {
        return player1Id.equals(userId) || userId.equals(player2Id);
    }
}
