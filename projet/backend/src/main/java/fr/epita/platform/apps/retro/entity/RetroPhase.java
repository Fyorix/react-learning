package fr.epita.platform.apps.retro.entity;

/** Phases dans l'ordre ; on ne passe qu'à la suivante. */
public enum RetroPhase {
    WRITING, GROUPING, VOTING, DISCUSSION, ACTIONS, CLOSED;

    public RetroPhase next() {
        return this == CLOSED ? null : values()[ordinal() + 1];
    }
}
