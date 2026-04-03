package com.smartscraper.entity;

/**
 * Enum representing the different scraping module types.
 */
public enum ModuleType {
    RESEARCH("Research"),
    NEWS("Real-Time News"),
    ECOMMERCE("E-Commerce"),
    SEARCH("Normal Search");

    private final String displayName;

    ModuleType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
