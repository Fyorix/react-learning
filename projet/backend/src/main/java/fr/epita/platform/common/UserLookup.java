package fr.epita.platform.common;

import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Résolution des noms d'affichage, utilisée par les mini-applications pour construire leurs DTO. */
@Component
@RequiredArgsConstructor
public class UserLookup {

    private final UserRepository users;

    public Map<UUID, String> names(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
    }

    public String name(UUID id) {
        return id == null ? null : users.findById(id).map(User::getDisplayName).orElse("Utilisateur supprimé");
    }

    public User require(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur"));
    }

    public <T> Map<UUID, String> namesOf(Collection<T> items, Function<T, UUID> idGetter) {
        return names(items.stream().map(idGetter).filter(java.util.Objects::nonNull).toList());
    }
}
