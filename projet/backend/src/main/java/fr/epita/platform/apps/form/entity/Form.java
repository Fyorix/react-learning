package fr.epita.platform.apps.form.entity;

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
@Table(name = "form_forms")
@Getter
@Setter
@NoArgsConstructor
public class Form extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column(nullable = false)
    private String title;
    private String description;
    @Column(nullable = false)
    private boolean anonymous;
    @Column(name = "one_response_per_user", nullable = false)
    private boolean oneResponsePerUser;
    @Column(name = "closes_at")
    private Instant closesAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormStatus status;
    @Column(name = "published_version")
    private Integer publishedVersion;
    @Column(name = "draft_version")
    private Integer draftVersion;
}
