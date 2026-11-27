package fr.epita.platform.apps.recruitment.entity;

import java.util.List;

/** Étapes ordonnées du pipeline ; REJECTED est accessible depuis toute étape non finale. */
public enum Stage {
    SCREENING, HR_INTERVIEW, TECH_INTERVIEW, OFFER, HIRED, REJECTED;

    public static final List<Stage> PIPELINE = List.of(SCREENING, HR_INTERVIEW, TECH_INTERVIEW, OFFER, HIRED);

    public boolean isFinal() {
        return this == HIRED || this == REJECTED;
    }
}
