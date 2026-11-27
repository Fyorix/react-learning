package fr.epita.platform.apps.oncall.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "oncall_swaps")
@Getter
@Setter
@NoArgsConstructor
public class Swap extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;
    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;
    @Column(name = "my_week", nullable = false)
    private LocalDate myWeek;
    @Column(name = "other_user_id", nullable = false)
    private UUID otherUserId;
    @Column(name = "other_week", nullable = false)
    private LocalDate otherWeek;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SwapStatus status;
    @Column(name = "decided_by")
    private UUID decidedBy;
}
