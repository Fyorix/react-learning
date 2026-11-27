package fr.epita.platform.apps.kudos.repository;

import fr.epita.platform.apps.kudos.entity.Kudo;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Critères du mur : filtres facultatifs et position après le curseur (date de création, puis identifiant). */
public final class KudoSpecs {

    private KudoSpecs() {
    }

    public static Specification<Kudo> wall(UUID valueId, UUID userId, Instant cursorAt, UUID cursorId) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isFalse(root.get("moderated")));
            if (valueId != null) {
                where.add(cb.equal(root.get("valueId"), valueId));
            }
            if (userId != null) {
                where.add(cb.or(cb.equal(root.get("authorId"), userId), cb.isMember(userId, root.get("recipientIds"))));
            }
            if (cursorAt != null) {
                where.add(cb.or(cb.lessThan(root.get("createdAt"), cursorAt),
                        cb.and(cb.equal(root.get("createdAt"), cursorAt), cb.lessThan(root.get("id"), cursorId))));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
