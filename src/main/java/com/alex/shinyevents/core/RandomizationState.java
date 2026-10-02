package com.alex.shinyevents.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Saved random-event preferences and the absolute deadline for the next attempt. */
public record RandomizationState(boolean enabled, Set<EventType> pool, long nextStartEpochMillis) {
    public RandomizationState {
        if (pool == null) {
            throw new IllegalArgumentException("Random event pool must contain only event types.");
        }
        EnumSet<EventType> copy = EnumSet.noneOf(EventType.class);
        for (EventType type : pool) {
            if (type == null) {
                throw new IllegalArgumentException("Random event pool must contain only event types.");
            }
            copy.add(type);
        }
        pool = Collections.unmodifiableSet(copy);
        if (enabled && pool.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one event type before enabling random events.");
        }
        if (nextStartEpochMillis < 0) {
            throw new IllegalArgumentException("The next random event deadline cannot be negative.");
        }
    }

    public static RandomizationState disabled() {
        return new RandomizationState(false, Set.of(), 0);
    }
}
