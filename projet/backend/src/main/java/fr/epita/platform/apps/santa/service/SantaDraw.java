package fr.epita.platform.apps.santa.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Algorithme du tirage : chaque participant offre à un autre et reçoit d'un autre.
 * Contraintes : pas soi-même, exclusions respectées, pas de paire réciproque au-delà de 3 participants.
 * Recherche par retour arrière sur un ordre aléatoire : exhaustive (on sait dire « impossible »)
 * et uniforme en pratique pour les tailles de groupe visées.
 */
public final class SantaDraw {

    private SantaDraw() {
    }

    /** @return giver → receiver, ou vide si aucune combinaison ne respecte les contraintes */
    public static Optional<Map<UUID, UUID>> draw(List<UUID> participants, Set<Pair> exclusions,
                                                 RandomGenerator random) {
        List<UUID> givers = new ArrayList<>(participants);
        shuffle(givers, random);
        boolean noReciprocal = participants.size() > 3;
        Map<UUID, UUID> result = new HashMap<>();
        return solve(givers, 0, participants, exclusions, noReciprocal, result, new java.util.HashSet<>(), random)
                ? Optional.of(result) : Optional.empty();
    }

    private static boolean solve(List<UUID> givers, int index, List<UUID> participants, Set<Pair> exclusions,
                                 boolean noReciprocal, Map<UUID, UUID> result, Set<UUID> taken,
                                 RandomGenerator random) {
        if (index == givers.size()) {
            return true;
        }
        UUID giver = givers.get(index);
        List<UUID> candidates = new ArrayList<>(participants);
        shuffle(candidates, random);
        for (UUID receiver : candidates) {
            if (receiver.equals(giver) || taken.contains(receiver)
                    || exclusions.contains(new Pair(giver, receiver))
                    || (noReciprocal && giver.equals(result.get(receiver)))) {
                continue;
            }
            result.put(giver, receiver);
            taken.add(receiver);
            if (solve(givers, index + 1, participants, exclusions, noReciprocal, result, taken, random)) {
                return true;
            }
            result.remove(giver);
            taken.remove(receiver);
        }
        return false;
    }

    private static void shuffle(List<UUID> list, RandomGenerator random) {
        for (int i = list.size() - 1; i > 0; i--) {
            Collections.swap(list, i, random.nextInt(i + 1));
        }
    }

    public record Pair(UUID giver, UUID receiver) {
    }
}
