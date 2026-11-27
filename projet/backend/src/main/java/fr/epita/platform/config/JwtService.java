package fr.epita.platform.config;

import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.entity.Role;
import fr.epita.platform.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String TYPE_CLAIM = "typ";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET doit contenir au moins 32 caractères");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = props.jwt().accessTtl();
        this.refreshTtl = props.jwt().refreshTtl();
        this.clock = clock;
    }

    public String accessToken(User user) {
        return build(user, ACCESS, accessTtl);
    }

    public String refreshToken(User user) {
        return build(user, REFRESH, refreshTtl);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private String build(User user, String type, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim(TYPE_CLAIM, type)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Jeton d'accès valide → utilisateur ; sinon {@code null}. */
    public AuthUser parseAccess(String token) {
        try {
            Claims claims = parse(token);
            if (!ACCESS.equals(claims.get(TYPE_CLAIM, String.class))) {
                return null;
            }
            return new AuthUser(UUID.fromString(claims.getSubject()), claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Jeton de rafraîchissement → identifiant utilisateur, sinon 401. */
    public UUID parseRefresh(String token) {
        try {
            Claims claims = parse(token);
            if (!REFRESH.equals(claims.get(TYPE_CLAIM, String.class))) {
                throw invalid();
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build()
                .parseSignedClaims(token).getPayload();
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Jeton invalide ou expiré");
    }
}
