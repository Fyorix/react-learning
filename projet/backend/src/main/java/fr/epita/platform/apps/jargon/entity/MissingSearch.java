package fr.epita.platform.apps.jargon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "jargon_missing")
@Getter
@Setter
@NoArgsConstructor
public class MissingSearch {

    @Id
    private String normalized;
    @Column(nullable = false)
    private String query;
    @Column(nullable = false)
    private int hits;
    @Column(name = "last_at", nullable = false)
    private Instant lastAt;
}
