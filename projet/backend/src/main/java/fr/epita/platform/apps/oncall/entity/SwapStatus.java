package fr.epita.platform.apps.oncall.entity;

public enum SwapStatus {
    /** Demandé par le titulaire, en attente du collègue. */
    REQUESTED,
    /** Accepté par le collègue, en attente du manager. */
    ACCEPTED,
    /** Validé par le manager : effectif. */
    APPROVED,
    REJECTED
}
