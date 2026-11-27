package fr.epita.platform.simulation.service;

import java.time.Duration;

/**
 * Attente côté serveur (jamais une boucle active). Remplaçable dans les tests pour ne pas attendre 60 s.
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
