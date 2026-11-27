package fr.epita.platform.apps.birthday.repository;

import fr.epita.platform.apps.birthday.entity.BirthdayProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BirthdayProfileRepository extends JpaRepository<BirthdayProfile, UUID> {
}
