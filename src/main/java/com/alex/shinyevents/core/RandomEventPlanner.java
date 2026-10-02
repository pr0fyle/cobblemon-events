package com.alex.shinyevents.core;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Pure random selections; scheduling and persistence are handled by the manager. */
public final class RandomEventPlanner {
    public static final int MIN_DURATION_MINUTES = 5;
    public static final int MAX_DURATION_MINUTES = 20;
    public static final int MIN_START_INTERVAL_MINUTES = 60;
    public static final int MAX_START_INTERVAL_MINUTES = 150;
    public static final long MILLIS_PER_MINUTE = 60_000L;

    private RandomEventPlanner() {}

    public static ActiveEvent chooseEvent(Set<EventType> pool, long startsAt, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        if (pool == null || pool.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one valid random event type.");
        }
        // Enum order makes seeded selection deterministic for every Set implementation.
        EnumSet<EventType> ordered = EnumSet.noneOf(EventType.class);
        for (EventType type : pool) {
            if (type == null) {
                throw new IllegalArgumentException("Choose at least one valid random event type.");
            }
            ordered.add(type);
        }
        EventType[] choices = ordered.toArray(EventType[]::new);
        EventType type = choices[random.nextInt(choices.length)];
        int multiplier = random.nextInt(minimumMultiplier(type), maximumMultiplier(type) + 1);
        long duration = random.nextInt(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES + 1) * MILLIS_PER_MINUTE;
        long endsAt;
        try {
            endsAt = Math.addExact(startsAt, duration);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Random event deadline is too large.", overflow);
        }
        return new ActiveEvent(type, multiplier, startsAt, endsAt);
    }

    public static long randomStartIntervalMillis(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return random.nextInt(MIN_START_INTERVAL_MINUTES, MAX_START_INTERVAL_MINUTES + 1) * MILLIS_PER_MINUTE;
    }

    public static int minimumMultiplier(EventType type) {
        return switch (type) {
            case SHINY -> 4;
            case ULTRARARE -> 2;
            case POKEMONXP -> 3;
            case BERRIES -> 5;
        };
    }

    public static int maximumMultiplier(EventType type) {
        return switch (type) {
            case SHINY -> 32;
            case ULTRARARE -> 4;
            case POKEMONXP -> 10;
            case BERRIES -> 20;
        };
    }
}
