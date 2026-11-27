package fr.epita.platform.apps.skills.repository;

import fr.epita.platform.apps.skills.entity.SkillProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SkillProfileRepository extends JpaRepository<SkillProfile, UUID> {

    /** Verrou du profil mentor pendant une acceptation : la capacité n'est jamais dépassée. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SkillProfile p where p.userId = :id")
    Optional<SkillProfile> lock(@Param("id") UUID id);
}
