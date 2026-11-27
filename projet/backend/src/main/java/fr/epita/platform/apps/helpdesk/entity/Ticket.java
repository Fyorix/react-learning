package fr.epita.platform.apps.helpdesk.entity;

import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.Category;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.Priority;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.TicketStatus;
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
@Table(name = "helpdesk_tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket extends BaseEntity {

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;
    @Column(name = "assignee_id")
    private UUID assigneeId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
