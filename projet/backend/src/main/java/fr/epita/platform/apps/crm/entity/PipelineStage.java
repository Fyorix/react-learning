package fr.epita.platform.apps.crm.entity;

/** Étapes ordonnées du pipeline et leur probabilité de signature. */
public enum PipelineStage {
    QUALIFICATION(10), PROPOSAL(40), NEGOTIATION(70), WON(100), LOST(0);

    private final int probability;

    PipelineStage(int probability) {
        this.probability = probability;
    }

    public int probability() {
        return probability;
    }

    public boolean closed() {
        return this == WON || this == LOST;
    }
}
