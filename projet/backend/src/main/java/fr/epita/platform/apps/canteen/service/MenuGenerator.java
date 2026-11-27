package fr.epita.platform.apps.canteen.service;

import fr.epita.platform.apps.canteen.entity.Dish;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Choix aléatoire des plats d'une catégorie pour les jours à remplir.
 * Les jours au choix le plus restreint sont remplis en premier.
 * Préférences, dans l'ordre : plat pas encore servi dans la semaine et différent des jours voisins,
 * puis simplement différent des jours voisins (catalogue trop petit), puis n'importe quel plat.
 */
public final class MenuGenerator {

    private MenuGenerator() {
    }

    /**
     * @param fixed    plats déjà posés sur les jours conservés (jour → plat)
     * @param toFill   jours à remplir, dans l'ordre de la semaine
     * @param poolFor  plats candidats pour un jour donné (permet d'imposer des jours végétariens)
     * @return jour → plat choisi, pour chaque jour de {@code toFill}
     */
    public static Map<DayOfWeek, Dish> pick(Map<DayOfWeek, UUID> fixed, List<DayOfWeek> toFill,
                                            java.util.function.Function<DayOfWeek, List<Dish>> poolFor,
                                            RandomGenerator random) {
        Map<DayOfWeek, UUID> week = new java.util.EnumMap<>(DayOfWeek.class);
        week.putAll(fixed);
        Map<DayOfWeek, Dish> chosen = new java.util.EnumMap<>(DayOfWeek.class);
        // Jours les plus contraints d'abord (ex. jours végétariens imposés) : sinon un jour libre peut
        // consommer un plat dont un jour contraint aura besoin, et forcer une répétition évitable.
        List<DayOfWeek> order = toFill.stream()
                .sorted(java.util.Comparator.comparingInt(d -> poolFor.apply(d).size()))
                .toList();
        for (DayOfWeek day : order) {
            List<Dish> pool = poolFor.apply(day);
            Set<UUID> used = new HashSet<>(week.values());
            Set<UUID> neighbours = new HashSet<>();
            if (day.getValue() > 1 && week.get(day.minus(1)) != null) {
                neighbours.add(week.get(day.minus(1)));
            }
            if (week.get(day.plus(1)) != null) {
                neighbours.add(week.get(day.plus(1)));
            }
            List<Dish> fresh = pool.stream().filter(d -> !used.contains(d.getId()) && !neighbours.contains(d.getId())).toList();
            List<Dish> notNeighbour = pool.stream().filter(d -> !neighbours.contains(d.getId())).toList();
            Dish dish = any(!fresh.isEmpty() ? fresh : !notNeighbour.isEmpty() ? notNeighbour : pool, random);
            chosen.put(day, dish);
            week.put(day, dish.getId());
        }
        return chosen;
    }

    /** Tire {@code count} jours au hasard parmi {@code days}. */
    public static Set<DayOfWeek> sample(Collection<DayOfWeek> days, int count, RandomGenerator random) {
        List<DayOfWeek> list = new ArrayList<>(days);
        for (int i = list.size() - 1; i > 0; i--) {
            java.util.Collections.swap(list, i, random.nextInt(i + 1));
        }
        return new HashSet<>(list.subList(0, Math.min(count, list.size())));
    }

    private static Dish any(List<Dish> list, RandomGenerator random) {
        return list.get(random.nextInt(list.size()));
    }
}
