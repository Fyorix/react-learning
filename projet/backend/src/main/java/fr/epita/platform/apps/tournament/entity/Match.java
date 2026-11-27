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
@Table(name = "tournament_matches")
@Getter
@Setter
@NoArgsConstructor
public class Match extends BaseEntity {

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;
    @Column(nullable = false)
    private int round;
    @Column(nullable = false)
    private int position;
    @Column(name = "entry_a_id")
    private UUID entryAId;
    @Column(name = "entry_b_id")
    private UUID entryBId;
    @Column(name = "score_a")
    private Integer scoreA;
    @Column(name = "score_b")
    private Integer scoreB;
    @Column(name = "reported_by")
    private UUID reportedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;
    @Column(name = "winner_id")
    private UUID winnerId;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
