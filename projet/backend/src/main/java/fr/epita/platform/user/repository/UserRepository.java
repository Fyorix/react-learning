package fr.epita.platform.user.repository;

import fr.epita.platform.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRoleAndEnabledTrue(fr.epita.platform.user.entity.Role role);

    /**
     * SELECT ... FROM users WHERE role = 'ADMIN' AND enabled FOR UPDATE.
     * Verrouille les lignes admin actives pour sérialiser les actions concurrentes (§4.4).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = fr.epita.platform.user.entity.Role.ADMIN and u.enabled = true")
    List<User> lockActiveAdmins();

    @Query("""
            select u from User u
            where :search is null
               or lower(u.email) like lower(concat('%', cast(:search as string), '%'))
               or lower(u.displayName) like lower(concat('%', cast(:search as string), '%'))
            """)
    Page<User> search(@Param("search") String search, Pageable pageable);

    List<User> findByEnabledTrueOrderByDisplayNameAsc();

    @Query("select count(u) from User u where u.role = fr.epita.platform.user.entity.Role.ADMIN and u.enabled = true")
    long lockActiveAdminsCount();
}
