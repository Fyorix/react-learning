package fr.epita.platform.apps.canteen.service;

import fr.epita.platform.apps.canteen.dto.CanteenDtos.DayMenuDto;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.DayMenuRequest;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.DishDto;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.DishRequest;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.GenerateRequest;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.WeekDto;
import fr.epita.platform.apps.canteen.dto.CanteenDtos.WeekSummaryDto;
import fr.epita.platform.apps.canteen.entity.DayMenu;
import fr.epita.platform.apps.canteen.entity.Dish;
import fr.epita.platform.apps.canteen.entity.DishCategory;
import fr.epita.platform.apps.canteen.entity.WeekPlan;
import fr.epita.platform.apps.canteen.entity.WeekStatus;
import fr.epita.platform.apps.canteen.repository.DishRepository;
import fr.epita.platform.apps.canteen.repository.WeekPlanRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CanteenService {

    private final DishRepository dishes;
    private final WeekPlanRepository weeks;
    private final RandomGenerator random;
    private final Clock clock;

    // ---------- catalogue ----------

    @Transactional(readOnly = true)
    public List<DishDto> dishes(DishCategory category, Boolean vegetarian, Boolean archived) {
        return dishes.findAllByOrderByCategoryAscNameAsc().stream()
                .filter(d -> category == null || d.getCategory() == category)
                .filter(d -> vegetarian == null || d.isVegetarian() == vegetarian)
                .filter(d -> archived == null || d.isArchived() == archived)
                .map(CanteenService::toDto).toList();
    }

    @Transactional
    public DishDto createDish(AuthUser me, DishRequest req) {
        me.requireAdmin();
        if (dishes.existsByCategoryAndNameIgnoreCase(req.category(), req.name().trim())) {
            throw ApiException.conflict("DISH_ALREADY_EXISTS", "Un plat porte déjà ce nom dans cette catégorie");
        }
        Dish dish = new Dish();
        apply(dish, req);
        return toDto(dishes.saveAndFlush(dish));
    }

    @Transactional
    public DishDto updateDish(AuthUser me, UUID id, DishRequest req) {
        me.requireAdmin();
        Dish dish = findDish(id);
        boolean renamed = !dish.getName().equalsIgnoreCase(req.name().trim()) || dish.getCategory() != req.category();
        if (renamed && dishes.existsByCategoryAndNameIgnoreCase(req.category(), req.name().trim())) {
            throw ApiException.conflict("DISH_ALREADY_EXISTS", "Un plat porte déjà ce nom dans cette catégorie");
        }
        if (dish.getCategory() != req.category() && !upcomingUsing(id).isEmpty()) {
            throw ApiException.conflict("DISH_IN_USE", "Le plat est au menu d'une semaine à venir : catégorie non modifiable");
        }
        apply(dish, req);
        return toDto(dishes.saveAndFlush(dish));
    }

    @Transactional
    public DishDto toggleArchive(AuthUser me, UUID id) {
        me.requireAdmin();
        Dish dish = findDish(id);
        dish.setArchived(!dish.isArchived());
        return toDto(dishes.saveAndFlush(dish));
    }

    @Transactional
    public void deleteDish(AuthUser me, UUID id) {
        me.requireAdmin();
        Dish dish = findDish(id);
        if (!upcomingUsing(id).isEmpty()) {
            throw ApiException.conflict("DISH_IN_USE", "Le plat est au menu d'une semaine à venir : archivez-le plutôt");
        }
        dishes.delete(dish);
    }

    // ---------- planning ----------

    /** USER : planning publié uniquement. ADMIN : planning ou semaine vierge. */
    @Transactional(readOnly = true)
    public WeekDto week(AuthUser me, String week) {
        LocalDate monday = IsoWeek.monday(week);
        WeekPlan plan = weeks.findByWeek(week).orElse(null);
        if (!me.isAdmin() && (plan == null || plan.getStatus() != WeekStatus.PUBLISHED)) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "MENU_NOT_PUBLISHED",
                    "Le menu de cette semaine n'est pas encore publié");
        }
        return toDto(week, monday, plan);
    }

    @Transactional
    public WeekDto setDay(AuthUser me, String week, String dayOfWeek, DayMenuRequest req) {
        me.requireAdmin();
        DayOfWeek day = IsoWeek.workDay(dayOfWeek);
        WeekPlan plan = editablePlan(week);
        Dish starter = requireCategory(req.starterId(), DishCategory.STARTER, "starterId");
        Dish main = requireCategory(req.mainId(), DishCategory.MAIN, "mainId");
        Dish dessert = requireCategory(req.dessertId(), DishCategory.DESSERT, "dessertId");
        DayMenu menu = plan.day(day).orElseGet(() -> newDay(plan, day));
        menu.setStarterId(starter.getId());
        menu.setMainId(main.getId());
        menu.setDessertId(dessert.getId());
        weeks.saveAndFlush(plan);
        return toDto(week, IsoWeek.monday(week), plan);
    }

    @Transactional
    public WeekDto clearDay(AuthUser me, String week, String dayOfWeek) {
        me.requireAdmin();
        DayOfWeek day = IsoWeek.workDay(dayOfWeek);
        WeekPlan plan = editablePlan(week);
        plan.getDays().removeIf(d -> d.getDayOfWeek() == day);
        weeks.saveAndFlush(plan);
        return toDto(week, IsoWeek.monday(week), plan);
    }

    @Transactional
    public WeekDto generate(AuthUser me, String week, GenerateRequest req) {
        me.requireAdmin();
        boolean overwrite = req != null && Boolean.TRUE.equals(req.overwrite());
        int vegetarianDays = req == null || req.vegetarianDays() == null ? 0 : req.vegetarianDays();
        WeekPlan plan = editablePlan(week);

        Map<DishCategory, List<Dish>> pools = new EnumMap<>(DishCategory.class);
        for (DishCategory c : DishCategory.values()) {
            List<Dish> pool = dishes.findByCategoryAndArchivedFalse(c);
            if (pool.isEmpty()) {
                throw ApiException.conflict("CATALOG_INCOMPLETE",
                        "Le catalogue ne contient aucun plat actif dans la catégorie " + c);
            }
            pools.put(c, pool);
        }
        if (overwrite) {
            plan.getDays().clear();
            weeks.saveAndFlush(plan);
        }
        List<DayOfWeek> toFill = IsoWeek.WORK_DAYS.stream()
                .filter(d -> plan.day(d).map(m -> m.getStarterId() == null || m.getMainId() == null
                        || m.getDessertId() == null).orElse(true))
                .toList();
        if (toFill.isEmpty()) {
            return toDto(week, IsoWeek.monday(week), plan);
        }

        // jours végétariens : on compte ceux déjà posés, on complète au hasard parmi les jours à remplir
        Set<UUID> vegMainIds = pools.get(DishCategory.MAIN).stream().filter(Dish::isVegetarian).map(Dish::getId)
                .collect(Collectors.toSet());
        long alreadyVeg = IsoWeek.WORK_DAYS.stream().filter(d -> !toFill.contains(d))
                .map(d -> plan.day(d).map(DayMenu::getMainId).orElse(null))
                .filter(id -> id != null && dishes.findById(id).map(Dish::isVegetarian).orElse(false)).count();
        int missingVeg = (int) Math.max(0, vegetarianDays - alreadyVeg);
        if (missingVeg > toFill.size()) {
            throw ApiException.conflict("VEGETARIAN_DAYS_UNREACHABLE",
                    "Pas assez de jours à remplir pour atteindre " + vegetarianDays + " jours végétariens");
        }
        if (missingVeg > 0 && vegMainIds.isEmpty()) {
            throw ApiException.conflict("NOT_ENOUGH_VEGETARIAN_DISHES", "Aucun plat principal végétarien actif");
        }
        Set<DayOfWeek> vegDays = MenuGenerator.sample(toFill, missingVeg, random);

        Map<DishCategory, Map<DayOfWeek, Dish>> picks = new EnumMap<>(DishCategory.class);
        for (DishCategory c : DishCategory.values()) {
            Map<DayOfWeek, UUID> fixed = new EnumMap<>(DayOfWeek.class);
            IsoWeek.WORK_DAYS.stream().filter(d -> !toFill.contains(d))
                    .forEach(d -> plan.day(d).map(m -> m.dish(c)).ifPresent(id -> fixed.put(d, id)));
            List<Dish> pool = pools.get(c);
            List<Dish> vegPool = pool.stream().filter(Dish::isVegetarian).toList();
            picks.put(c, MenuGenerator.pick(fixed, toFill,
                    d -> c == DishCategory.MAIN && vegDays.contains(d) ? vegPool : pool, random));
        }
        for (DayOfWeek d : toFill) {
            DayMenu menu = plan.day(d).orElseGet(() -> newDay(plan, d));
            menu.setStarterId(picks.get(DishCategory.STARTER).get(d).getId());
            menu.setMainId(picks.get(DishCategory.MAIN).get(d).getId());
            menu.setDessertId(picks.get(DishCategory.DESSERT).get(d).getId());
        }
        weeks.saveAndFlush(plan);
        return toDto(week, IsoWeek.monday(week), plan);
    }

    @Transactional
    public WeekDto publish(AuthUser me, String week) {
        me.requireAdmin();
        IsoWeek.monday(week);
        WeekPlan plan = weeks.findByWeek(week).orElseThrow(() -> ApiException.notFound("Planning"));
        long complete = plan.getDays().stream().filter(d -> d.getStarterId() != null && d.getMainId() != null
                && d.getDessertId() != null).count();
        if (complete < IsoWeek.WORK_DAYS.size()) {
            throw ApiException.conflict("MENU_INCOMPLETE", "Les 5 jours doivent être complets avant publication");
        }
        plan.setStatus(WeekStatus.PUBLISHED);
        weeks.saveAndFlush(plan);
        return toDto(week, IsoWeek.monday(week), plan);
    }

    @Transactional
    public WeekDto unpublish(AuthUser me, String week) {
        me.requireAdmin();
        IsoWeek.monday(week);
        WeekPlan plan = weeks.findByWeek(week).orElseThrow(() -> ApiException.notFound("Planning"));
        plan.setStatus(WeekStatus.DRAFT);
        weeks.saveAndFlush(plan);
        return toDto(week, IsoWeek.monday(week), plan);
    }

    @Transactional(readOnly = true)
    public List<WeekSummaryDto> list(AuthUser me, String from, String to) {
        me.requireAdmin();
        if (from != null) {
            IsoWeek.monday(from);
        }
        if (to != null) {
            IsoWeek.monday(to);
        }
        return weeks.findAllByOrderByWeekAsc().stream()
                .filter(w -> from == null || w.getWeek().compareTo(from) >= 0)
                .filter(w -> to == null || w.getWeek().compareTo(to) <= 0)
                .map(w -> new WeekSummaryDto(w.getWeek(), IsoWeek.monday(w.getWeek()), w.getStatus(),
                        (int) w.getDays().stream().filter(d -> d.getStarterId() != null && d.getMainId() != null
                                && d.getDessertId() != null).count()))
                .toList();
    }

    // ---------- interne ----------

    private WeekPlan editablePlan(String week) {
        IsoWeek.monday(week);
        WeekPlan plan = weeks.findByWeek(week).orElseGet(() -> {
            WeekPlan p = new WeekPlan();
            p.setWeek(week);
            return weeks.saveAndFlush(p);
        });
        if (plan.getStatus() == WeekStatus.PUBLISHED) {
            throw ApiException.conflict("MENU_PUBLISHED", "Le planning est publié : repassez-le en brouillon pour le modifier");
        }
        return plan;
    }

    private static DayMenu newDay(WeekPlan plan, DayOfWeek day) {
        DayMenu menu = new DayMenu();
        menu.setWeekPlan(plan);
        menu.setDayOfWeek(day);
        plan.getDays().add(menu);
        return menu;
    }

    private Dish requireCategory(UUID id, DishCategory category, String field) {
        Dish dish = dishes.findById(id).orElseThrow(() -> ValidationException.field(field, "Plat introuvable"));
        if (dish.getCategory() != category) {
            throw ValidationException.field(field, "Ce plat n'est pas de la catégorie " + category);
        }
        if (dish.isArchived()) {
            throw ValidationException.field(field, "Ce plat est archivé");
        }
        return dish;
    }

    private List<WeekPlan> upcomingUsing(UUID dishId) {
        return weeks.findUpcomingUsing(dishId, IsoWeek.of(LocalDate.now(clock)));
    }

    private void apply(Dish dish, DishRequest req) {
        dish.setName(req.name().trim());
        dish.setCategory(req.category());
        dish.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        dish.setVegetarian(req.vegetarian());
        List<String> allergens = req.allergens() == null ? List.of()
                : req.allergens().stream().map(String::trim).map(String::toLowerCase).distinct().toList();
        dish.setAllergens(allergens.isEmpty() ? null : String.join(",", allergens));
    }

    private Dish findDish(UUID id) {
        return dishes.findById(id).orElseThrow(() -> ApiException.notFound("Plat"));
    }

    private static DishDto toDto(Dish d) {
        List<String> allergens = d.getAllergens() == null ? List.of() : Arrays.asList(d.getAllergens().split(","));
        return new DishDto(d.getId(), d.getName(), d.getCategory(), d.getDescription(), d.isVegetarian(), allergens,
                d.isArchived());
    }

    private WeekDto toDto(String week, LocalDate monday, WeekPlan plan) {
        Map<UUID, DishDto> catalog = dishes.findAll().stream().collect(Collectors.toMap(Dish::getId, CanteenService::toDto));
        List<DayMenuDto> days = new ArrayList<>();
        for (DayOfWeek d : IsoWeek.WORK_DAYS) {
            DayMenu menu = plan == null ? null : plan.day(d).orElse(null);
            DishDto s = menu == null || menu.getStarterId() == null ? null : catalog.get(menu.getStarterId());
            DishDto m = menu == null || menu.getMainId() == null ? null : catalog.get(menu.getMainId());
            DishDto ds = menu == null || menu.getDessertId() == null ? null : catalog.get(menu.getDessertId());
            days.add(new DayMenuDto(d, monday.plusDays(d.getValue() - 1L), s, m, ds, s != null && m != null && ds != null));
        }
        return new WeekDto(week, monday, plan == null ? WeekStatus.DRAFT : plan.getStatus(), plan != null,
                plan == null ? null : plan.getUpdatedAt(), days, IsoWeek.of(monday.minusWeeks(1)),
                IsoWeek.of(monday.plusWeeks(1)));
    }
}
