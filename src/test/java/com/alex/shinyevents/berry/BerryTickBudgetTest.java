package com.alex.shinyevents.berry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class BerryTickBudgetTest {
    @Test
    void inactiveOrInvalidBoostNeverRequestsExtraTicksOrRandomness() {
        for (double multiplier : new double[]{-3, 0, 1, Double.NaN,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertEquals(0, BerryTickBudget.extraTicks(multiplier,
                    () -> fail("Inactive growth must not consume randomness")));
        }
    }

    @Test
    void integralBoostsUseExactExtraTicksWithoutRandomness() {
        for (int multiplier = 2; multiplier <= 64; multiplier++) {
            assertEquals(multiplier - 1, BerryTickBudget.extraTicks(multiplier,
                    () -> fail("Integral growth must not consume randomness")));
        }
    }

    @Test
    void fractionalBoostUsesBothSidesOfTheProbabilityBoundary() {
        assertEquals(2, BerryTickBudget.extraTicks(2.5, () -> 0.49999));
        assertEquals(1, BerryTickBudget.extraTicks(2.5, () -> 0.5));
        assertEquals(1, BerryTickBudget.extraTicks(1.25, () -> 0.24999));
        assertEquals(0, BerryTickBudget.extraTicks(1.25, () -> 0.25));
    }

    @Test
    void fractionalRatesRetainTheirExpectedSpeedOverUniformSamples() {
        // Sweep a uniform distribution, rather than relying on a flaky random test.
        for (double multiplier : new double[]{1.01, 1.1, 1.5, 2.5, 7.75, 63.999}) {
            int samples = 10_000;
            int totalTicks = 0;
            for (int sample = 0; sample < samples; sample++) {
                double value = (sample + 0.5) / samples;
                totalTicks += 1 + BerryTickBudget.extraTicks(multiplier, () -> value);
            }
            assertEquals(multiplier * samples, totalTicks, 0.00001);
        }
    }

    @Test
    void excessiveBoostsStayWithinThePerTickWorkLimit() {
        for (double multiplier : new double[]{64, 64.5, 100, Double.MAX_VALUE}) {
            assertEquals(63, BerryTickBudget.extraTicks(multiplier,
                    () -> fail("A clamped integral multiplier needs no randomness")));
        }
        assertEquals(63, BerryTickBudget.extraTicks(63.999, () -> 0));
    }
}
