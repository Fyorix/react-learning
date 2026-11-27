package fr.epita.platform.simulation.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulationConfig {

    @Bean
    public Sleeper sleeper() {
        return duration -> Thread.sleep(duration);
    }
}
