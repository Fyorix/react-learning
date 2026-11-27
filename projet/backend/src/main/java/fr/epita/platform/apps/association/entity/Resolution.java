package fr.epita.platform.apps.association.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Résolution d'AG : seul le décompte est stocké (vote secret). */
@Entity
@Table(name = "association_resolutions")
@Getter
@Setter
@NoArgsConstructor
public class Resolution {

    @Id
    private UUID id;
    @Column(name = "assembly_id", nullable = false)
    private UUID assemblyId;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String text;
    @Column(name = "for_count", nullable = false)
    private int forCount;
    @Column(name = "against_count", nullable = false)
    private int againstCount;
    @Column(name = "abstain_count", nullable = false)
    private int abstainCount;
}
