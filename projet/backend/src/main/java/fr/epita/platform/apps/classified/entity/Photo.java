package fr.epita.platform.apps.classified.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "classified_photos")
@Getter
@Setter
@NoArgsConstructor
public class Photo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;
    @Column(nullable = false)
    private int position;
    @Column(name = "file_key", nullable = false)
    private String fileKey;
    @Column(name = "content_type", nullable = false)
    private String contentType;
}
