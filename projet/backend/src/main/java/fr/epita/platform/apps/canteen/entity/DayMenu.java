package fr.epita.platform.apps.canteen.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.util.UUID;

@Entity
@Table(name = "canteen_day_menus")
@Getter
@Setter
@NoArgsConstructor
public class DayMenu {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_id")
    private WeekPlan weekPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "starter_id")
    private UUID starterId;

    @Column(name = "main_id")
    private UUID mainId;

    @Column(name = "dessert_id")
    private UUID dessertId;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID dish(DishCategory category) {
        return switch (category) {
            case STARTER -> starterId;
            case MAIN -> mainId;
            case DESSERT -> dessertId;
        };
    }
}
