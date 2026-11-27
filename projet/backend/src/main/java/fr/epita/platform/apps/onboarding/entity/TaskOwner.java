package fr.epita.platform.apps.onboarding.entity;

/** Responsable d'une tâche : l'arrivant, son manager, son parrain, ou les services IT / RH (cochées par un ADMIN). */
public enum TaskOwner {
    NEWCOMER, MANAGER, BUDDY, IT, HR
}
