package fr.epita.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.util.random.RandomGenerator;

@Configuration
public class AppConfig {

    /** Horloge injectable : les règles basées sur la date (retards, échéances, clôtures) restent testables. */
    @Bean
    public Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.zone()));
    }

    /** Générateur aléatoire injectable (tirage Secret Santa, génération des menus). */
    @Bean
    public RandomGenerator randomGenerator() {
        return new SecureRandom();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
