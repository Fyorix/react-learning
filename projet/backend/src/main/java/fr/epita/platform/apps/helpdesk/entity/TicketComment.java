package fr.epita.platform.apps.helpdesk.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "helpdesk_comments")
@Getter
@Setter
@NoArgsConstructor
public class TicketComment extends BaseEntity {

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;
    @Column(name = "author_id", nullable = false)
    private UUID authorId;
    @Column(nullable = false)
    private String content;
    @Column(nullable = false)
    private boolean internal;
}
