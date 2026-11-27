package fr.epita.platform.apps.tournament.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Générateur aléatoire injectable (départage des Elo égaux, ordre des poules) : remplaçable dans les tests. */
@Component
public class TournamentRandom {

    private final Random random = new SecureRandom();

    public <T> void shuffle(List<T> list) {
        Collections.shuffle(list, random);
    }
}
