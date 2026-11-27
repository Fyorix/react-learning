package fr.epita.platform.apps.kudos.repository;

import fr.epita.platform.apps.kudos.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReactionRepository extends JpaRepository<Reaction, Reaction.Key> {

    List<Reaction> findByIdKudoIdIn(Collection<java.util.UUID> kudoIds);
}
