package fr.epita.platform.apps.badge.entity;

/** Points d'expérience par rareté. */
public enum Rarity {
    COMMON(10), RARE(25), EPIC(50), LEGENDARY(100);

    public final int points;

    Rarity(int points) {
        this.points = points;
    }
}
