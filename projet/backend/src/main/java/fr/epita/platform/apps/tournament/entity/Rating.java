package fr.epita.platform.apps.tournament.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournament_ratings")
@Getter
@Setter
@NoArgsConstructor
public class Rating {

    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(nullable = false)
    private int elo;
    @Column(nullable = false)
    private int played;
    @Column(nullable = false)
    private int wins;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
