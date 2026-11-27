package fr.epita.platform.apps.classified.entity;

public final class ClassifiedEnums {

    private ClassifiedEnums() {
    }

    public enum ListingType { SALE, GIFT, SERVICE }

    /** EXPIRED est calculé à partir de la date d'expiration. */
    public enum ListingStatus { ACTIVE, RESERVED, CLOSED, EXPIRED }
}
