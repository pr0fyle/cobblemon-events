package com.alex.shinyevents.berry;

import java.util.function.DoubleSupplier;

/** Converts a speed multiplier into bounded extra ticks without losing fractional boosts. */
public final class BerryTickBudget {
    public static final double MAX_MULTIPLIER = 64.0;

    private BerryTickBudget() {}

    public static int extraTicks(double multiplier, DoubleSupplier random) {
        if (!Double.isFinite(multiplier) || multiplier <= 1.0) return 0;
        double extra = Math.min(multiplier, MAX_MULTIPLIER) - 1.0;
        int whole = (int) extra;
        double fraction = extra - whole;
        // Stochastic rounding is unbiased even when stage changes replace the ticker.
        // Integer multipliers do not consume the world's random sequence.
        return whole + (fraction > 0.0 && random.getAsDouble() < fraction ? 1 : 0);
    }
}
