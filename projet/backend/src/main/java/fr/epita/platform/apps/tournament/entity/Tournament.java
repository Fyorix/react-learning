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
@Table(name = "tournament_tournaments")
@Getter
@Setter
@NoArgsConstructor
public class Tournament extends BaseEntity {

    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Format format;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;
    @Column(name = "registration_deadline", nullable = false)
    private Instant registrationDeadline;
    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TournamentStatus status;
}
