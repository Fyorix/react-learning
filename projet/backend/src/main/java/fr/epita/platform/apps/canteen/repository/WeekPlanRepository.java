package fr.epita.platform.apps.canteen.repository;

import fr.epita.platform.apps.canteen.entity.WeekPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeekPlanRepository extends JpaRepository<WeekPlan, UUID> {

    Optional<WeekPlan> findByWeek(String week);

    List<WeekPlan> findAllByOrderByWeekAsc();

    /** Plannings de la semaine {@code fromWeek} incluse et suivantes qui utilisent le plat. */
    @Query("""
            select distinct w from WeekPlan w join w.days d
            where w.week >= :fromWeek and (d.starterId = :dishId or d.mainId = :dishId or d.dessertId = :dishId)
            """)
    List<WeekPlan> findUpcomingUsing(UUID dishId, String fromWeek);
}
