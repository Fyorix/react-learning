package fr.epita.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Admin admin, Cors cors, String zone) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {
    }

    public record Admin(String email, String password) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
