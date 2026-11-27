package fr.epita.platform.apps.kudos.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "kudos")
@Getter
@Setter
@NoArgsConstructor
public class Kudo extends BaseEntity {

    @Column(name = "author_id", nullable = false)
    private UUID authorId;
    @Column(nullable = false)
    private String message;
    @Column(name = "value_id", nullable = false)
    private UUID valueId;
    @Column(nullable = false)
    private boolean moderated;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kudos_recipients", joinColumns = @JoinColumn(name = "kudo_id"))
    @Column(name = "user_id")
    private Set<UUID> recipientIds = new LinkedHashSet<>();
}
