package fr.epita.platform.apps.kb.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kb_articles")
@Getter
@Setter
@NoArgsConstructor
public class KbArticle extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String slug;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "published_version")
    private Integer publishedVersion;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(nullable = false)
    private int views;
}
