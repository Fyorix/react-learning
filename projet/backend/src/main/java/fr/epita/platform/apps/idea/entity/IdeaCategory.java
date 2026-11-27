package fr.epita.platform.apps.idea.entity;

public enum IdeaCategory {
    PRODUCT("Produit"),
    ORGANISATION("Organisation"),
    TOOLS("Outils"),
    WELLBEING("Bien-être"),
    OTHER("Autre");

    private final String label;

    IdeaCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
