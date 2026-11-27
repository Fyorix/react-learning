package fr.epita.platform.apps.tournament.entity;

public enum MatchStatus {
    /** Adversaires pas encore connus. */
    WAITING,
    /** À jouer : score à saisir. */
    SCHEDULED,
    /** Score saisi, en attente de confirmation adverse. */
    REPORTED,
    CONFIRMED,
    /** Exempt : qualifié d'office. */
    BYE
}
