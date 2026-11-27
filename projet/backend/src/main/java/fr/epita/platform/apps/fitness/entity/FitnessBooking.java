package fr.epita.platform.apps.fitness.entity;

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
@Table(name = "fitness_bookings")
@Getter
@Setter
@NoArgsConstructor
public class FitnessBooking extends BaseEntity {

    @Column(name = "class_id", nullable = false)
    private UUID classId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FitnessBookingStatus status;
    @Column(nullable = false)
    private boolean debited;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
