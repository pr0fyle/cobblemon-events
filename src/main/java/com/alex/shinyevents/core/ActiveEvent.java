package com.alex.shinyevents.core;

/** An event's immutable settings and absolute expiry, independent of Minecraft ticks. */
public record ActiveEvent(EventType type, double multiplier, long startedAtEpochMillis, long endsAtEpochMillis) {
    public ActiveEvent {
        EventArguments.validateMultiplier(type, multiplier);
        long duration;
        try {
            duration = Math.subtractExact(endsAtEpochMillis, startedAtEpochMillis);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Event duration is too large.", e);
        }
        if (duration <= 0 || duration > EventArguments.MAX_DURATION_MILLIS) {
            throw new IllegalArgumentException("Event duration must be positive and at most 7 days.");
        }
    }

    public long remainingMillis(long nowEpochMillis) {
        if (nowEpochMillis >= endsAtEpochMillis) {
            return 0;
        }
        if (nowEpochMillis <= startedAtEpochMillis) {
            return endsAtEpochMillis - startedAtEpochMillis;
        }
        return endsAtEpochMillis - nowEpochMillis;
    }

    public long remainingSeconds(long nowEpochMillis) {
        return (remainingMillis(nowEpochMillis) + 999) / 1_000;
    }

    public float progress(long nowEpochMillis) {
        return (float) ((double) remainingMillis(nowEpochMillis)
                / (endsAtEpochMillis - startedAtEpochMillis));
    }

    /** A backward clock adjustment keeps this already-started event active. */
    public boolean isActive(long nowEpochMillis) {
        return nowEpochMillis < endsAtEpochMillis;
    }

    /**
     * Cobblemon 1.7.3 treats every positive shiny rate as a probability denominator.
     * Fractional denominators already guarantee a shiny. Disabled or non-finite
     * rates remain unchanged; the boosted denominator is clamped to 1.
     */
    public static float boostShinyRate(float baseRate, double multiplier) {
        EventArguments.validateMultiplier(EventType.SHINY, multiplier);
        if (!Float.isFinite(baseRate) || baseRate <= 0) {
            return baseRate;
        }
        return (float) Math.max(1, baseRate / multiplier);
    }
}
