package fr.epita.platform.apps.birthday.repository;

import fr.epita.platform.apps.birthday.entity.BirthdayTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BirthdayTeamRepository extends JpaRepository<BirthdayTeam, UUID> {

    List<BirthdayTeam> findAllByOrderByNameAsc();

    Optional<BirthdayTeam> findByNameIgnoreCase(String name);
}
