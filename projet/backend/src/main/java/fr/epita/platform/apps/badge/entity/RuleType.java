package fr.epita.platform.apps.badge.entity;

public enum RuleType {
    /** Nombre d'événements ≥ seuil. */
    COUNT,
    /** Jours consécutifs avec au moins un événement ≥ seuil. */
    STREAK,
    /** Trois paliers (bronze, argent, or) sur le nombre d'événements. */
    TIERED,
    /** Attribution manuelle par un ADMIN uniquement. */
    MANUAL
}
