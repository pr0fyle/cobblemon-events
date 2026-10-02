package com.alex.shinyevents.effects;

import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/** Pure, bounded arithmetic shared by the Cobblemon event adapters. */
final class EffectMath {
    private EffectMath() {}

    /**
     * Promotes a fraction of non-target selections, giving the target a final
     * probability of min(1, multiplier * its original probability). Existing
     * target selections are kept, and other buckets keep their relative odds.
     * The supplied weights are read only, including when they are invalid.
     */
    static <T> T boostBucket(Map<T, Float> weights, T selected,
                             Predicate<T> isTarget, double multiplier, DoubleSupplier random) {
        if (!Double.isFinite(multiplier) || multiplier <= 1 || selected == null
                || weights == null || weights.isEmpty() || isTarget.test(selected)) {
            return selected;
        }

        T target = null;
        double targetWeight = 0;
        double totalWeight = 0;
        for (var entry : weights.entrySet()) {
            var weight = entry.getValue();
            if (entry.getKey() == null || weight == null || !Float.isFinite(weight) || weight < 0) {
                return selected;
            }
            totalWeight += weight;
            if (isTarget.test(entry.getKey())) {
                // Ambiguous target categories cannot be boosted reliably.
                if (target != null) return selected;
                target = entry.getKey();
                targetWeight = weight;
            }
        }
        Float selectedWeight = weights.get(selected);
        if (target == null || selectedWeight == null || selectedWeight <= 0) {
            // Preserve another addon's choice of an otherwise unavailable bucket.
            return selected;
        }

        double probability = promotionProbability(targetWeight, totalWeight, multiplier);
        if (probability <= 0) return selected;
        if (probability >= 1) return target;
        double roll = random.getAsDouble();
        return roll >= 0 && roll < probability ? target : selected;
    }

    static double promotionProbability(double targetWeight, double totalWeight, double multiplier) {
        if (!Double.isFinite(targetWeight) || !Double.isFinite(totalWeight)
                || !Double.isFinite(multiplier) || multiplier <= 1
                || targetWeight <= 0 || totalWeight <= targetWeight) {
            return 0;
        }
        double baseProbability = targetWeight / totalWeight;
        double boostedProbability = Math.min(1, multiplier * baseProbability);
        return Math.clamp((boostedProbability - baseProbability) / (1 - baseProbability), 0, 1);
    }

    /**
     * Cobblemon adds the award to an int before enforcing the level cap. Limit
     * the boosted award to the remaining int capacity so that addition is safe.
     * Inactive boosts and non-positive awards are left untouched.
     */
    static int boostExperience(int award, int currentExperience, double multiplier) {
        if (award <= 0 || !Double.isFinite(multiplier) || multiplier <= 1) return award;
        long headroom = (long) Integer.MAX_VALUE - Math.max(0, currentExperience);
        double boosted = award * multiplier;
        if (boosted >= headroom) return (int) headroom;
        return (int) Math.min(headroom, Math.round(boosted));
    }
}
