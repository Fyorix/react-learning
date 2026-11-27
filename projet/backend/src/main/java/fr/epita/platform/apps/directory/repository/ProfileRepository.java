package fr.epita.platform.apps.directory.repository;

import fr.epita.platform.apps.directory.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    List<Profile> findByTeamId(UUID teamId);

    List<Profile> findByManagerId(UUID managerId);
}
