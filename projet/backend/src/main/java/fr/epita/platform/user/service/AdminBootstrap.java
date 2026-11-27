package fr.epita.platform.user.service;

import fr.epita.platform.config.AppProperties;
import fr.epita.platform.user.entity.Role;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Amorçage (§4.4) : sans admin actif, en créer un depuis ADMIN_EMAIL / ADMIN_PASSWORD,
 * ou refuser de démarrer si ces variables manquent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRoleAndEnabledTrue(Role.ADMIN)) {
            return;
        }
        String email = props.admin().email();
        String password = props.admin().password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Aucun administrateur actif en base : définir ADMIN_EMAIL et ADMIN_PASSWORD pour en créer un au démarrage.");
        }
        String normalized = UserService.normalize(email);
        User admin = users.findByEmail(normalized).orElseGet(User::new);
        admin.setEmail(normalized);
        admin.setPasswordHash(passwordEncoder.encode(password));
        if (admin.getDisplayName() == null) {
            admin.setDisplayName("Administrateur");
        }
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        users.save(admin);
        log.info("Administrateur initial créé : {}", normalized);
    }
}
