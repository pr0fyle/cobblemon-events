package com.alex.shinyevents.core;

import java.util.Locale;

/** Stable command and persistence identifiers for independently timed events. */
public enum EventType {
    SHINY("shiny", "Shiny", EventArguments.MAX_MULTIPLIER),
    ULTRARARE("ultrarare", "Ultra-Rare Spawns", EventArguments.MAX_MULTIPLIER),
    POKEMONXP("pokemonxp", "Pokémon Battle XP", EventArguments.MAX_MULTIPLIER),
    BERRIES("berries", "Berry Growth", 64);

    private final String id;
    private final String displayName;
    private final double maximumMultiplier;

    EventType(String id, String displayName, double maximumMultiplier) {
        this.id = id;
        this.displayName = displayName;
        this.maximumMultiplier = maximumMultiplier;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public double maximumMultiplier() {
        return maximumMultiplier;
    }

    public static EventType fromId(String id) {
        if (id != null) {
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            for (EventType type : values()) {
                if (type.id.equals(normalized)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + id);
    }
}
