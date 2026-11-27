package fr.epita.platform.apps.canteen.repository;

import fr.epita.platform.apps.canteen.entity.Dish;
import fr.epita.platform.apps.canteen.entity.DishCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DishRepository extends JpaRepository<Dish, UUID> {

    List<Dish> findAllByOrderByCategoryAscNameAsc();

    List<Dish> findByCategoryAndArchivedFalse(DishCategory category);

    boolean existsByCategoryAndNameIgnoreCase(DishCategory category, String name);
}
