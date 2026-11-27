package fr.epita.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.config.JwtService;
import fr.epita.platform.simulation.service.SimulationService;
import fr.epita.platform.user.entity.Role;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

/**
 * Base des tests d'intégration : un PostgreSQL 16 (Testcontainers) partagé par toutes les classes,
 * base vidée avant chaque test.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-with-at-least-32-characters-long",
        "app.admin.email=bootstrap@test.fr",
        "app.admin.password=bootstrap-password",
        "logging.level.root=WARN",
        "spring.main.banner-mode=off"
})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    protected static final String PASSWORD = "password123";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected SimulationService simulationService;

    @BeforeEach
    void cleanDatabase() {
        simulationService.reset("tests");
        List<String> tables = jdbc.queryForList(
                "select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'",
                String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }

    protected User createUser(String name, Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(name.toLowerCase().replace(' ', '.') + "@test.fr");
        user.setDisplayName(name);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    protected User admin(String name) {
        return createUser(name, Role.ADMIN);
    }

    protected User user(String name) {
        return createUser(name, Role.USER);
    }

    protected AuthUser auth(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getRole());
    }

    protected String bearer(User user) {
        return "Bearer " + jwtService.accessToken(user);
    }

    protected String toJson(Object value) throws Exception {
        return json.writeValueAsString(value);
    }
}
