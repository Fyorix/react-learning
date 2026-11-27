package fr.epita.platform.apps.kb.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "kb_versions")
@Getter
@Setter
@NoArgsConstructor
public class KbVersion extends BaseEntity {

    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    @Column(nullable = false)
    private int number;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String category;
    /** Tags séparés par des virgules, en minuscules. */
    @Column(nullable = false)
    private String tags = "";
    @Column(nullable = false)
    private String content;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status;
    @Column(name = "author_id")
    private UUID authorId;
    @Column(name = "review_comment")
    private String reviewComment;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "restored_from")
    private Integer restoredFrom;

    public List<String> tagList() {
        return tags.isBlank() ? List.of() : Arrays.asList(tags.split(","));
    }
}
