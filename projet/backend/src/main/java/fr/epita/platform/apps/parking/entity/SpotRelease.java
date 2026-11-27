package fr.epita.platform.apps.parking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Jour où le titulaire libère sa place. */
@Entity
@Table(name = "parking_releases")
@Getter
@Setter
@NoArgsConstructor
public class SpotRelease {

    @EmbeddedId
    private Key id;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "spot_id")
        private UUID spotId;
        @Column(name = "day")
        private LocalDate day;
    }
}
