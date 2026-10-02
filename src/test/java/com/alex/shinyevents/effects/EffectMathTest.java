package com.alex.shinyevents.effects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectMathTest {
    private static final String TARGET = "ultra-rare";
    private static final Map<String, Float> WEIGHTS = Map.of(
            "common", 94.3f, "uncommon", 5f, "rare", 0.5f, TARGET, 0.2f);

    @ParameterizedTest
    @CsvSource({"0.002,2", "0.002,8", "0.002,1000", "0.1,2", "0.1,8", "0.9,2"})
    void promotionGivesExactCappedMultiplier(double original, double multiplier) {
        double promotion = EffectMath.promotionProbability(original, 1, multiplier);
        double finalProbability = original + (1 - original) * promotion;
        assertEquals(Math.min(1, original * multiplier), finalProbability, 1e-14);
        assertTrue(promotion >= 0 && promotion <= 1);
    }

    @Test
    void survivorsRetainRelativeNonTargetDistribution() {
        double promotion = EffectMath.promotionProbability(0.2, 100, 8);
        double commonAfter = 94.3 / 100 * (1 - promotion);
        double rareAfter = 0.5 / 100 * (1 - promotion);
        assertEquals(94.3 / 0.5, commonAfter / rareAfter, 1e-12);
    }

    @Test
    void fractionalMultipliersWork() {
        double promotion = EffectMath.promotionProbability(1, 10, 1.5);
        assertEquals(0.15, 0.1 + 0.9 * promotion, 1e-14);
    }

    @Test
    void selectionChangesOnlyWhenPromotionRollSucceeds() {
        double sum = WEIGHTS.values().stream().mapToDouble(Float::doubleValue).sum();
        double threshold = EffectMath.promotionProbability(WEIGHTS.get(TARGET), sum, 2);
        assertEquals(TARGET, choose(WEIGHTS, "common", 2, 0));
        assertEquals("common", choose(WEIGHTS, "common", 2, threshold));
        assertEquals(TARGET, choose(WEIGHTS, "common", 2, Math.nextDown(threshold)));
        assertEquals("common", choose(WEIGHTS, "common", 2, 0.99));
    }

    @Test
    void keepsAlreadyChosenUltraRareWithoutConsumingRandomness() {
        assertEquals(TARGET, EffectMath.boostBucket(WEIGHTS, TARGET, TARGET::equals, 8,
                () -> { throw new AssertionError("Existing ultra-rare selections must be preserved"); }));
    }

    @Test
    void capMakesEveryEligibleSelectionUltraRare() {
        assertEquals(TARGET, choose(WEIGHTS, "common", 1000, Math.nextDown(1.0)));
    }

    @Test
    void keepsWeightsUntouched() {
        var mutable = new HashMap<>(WEIGHTS);
        choose(mutable, "common", 8, 0);
        assertEquals(WEIGHTS, mutable);
        assertEquals(TARGET, choose(WEIGHTS, "common", 8, 0));
    }

    @Test
    void absentOrDisabledTargetCannotBeResurrected() {
        assertEquals("common", choose(Map.of("common", 100f), "common", 1000, 0));
        assertEquals("common", choose(Map.of("common", 100f, TARGET, 0f), "common", 1000, 0));
    }

    @Test
    void respectsUnrepresentedSelectionsMadeByOtherAddons() {
        assertEquals("custom", choose(WEIGHTS, "custom", 1000, 0));
        assertEquals("custom", choose(Map.of("custom", 0f, TARGET, 1f), "custom", 1000, 0));
    }

    @ParameterizedTest
    @ValueSource(floats = {-1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void rejectsInvalidWeightAnywhereInMap(float invalid) {
        var weights = new HashMap<>(WEIGHTS);
        weights.put("rare", invalid);
        assertEquals("common", choose(weights, "common", 1000, 0));
    }

    @Test
    void rejectsNullWeightsOrKeys() {
        var weights = new HashMap<>(WEIGHTS);
        weights.put("rare", null);
        assertEquals("common", choose(weights, "common", 1000, 0));
        weights = new HashMap<>(WEIGHTS);
        weights.put(null, 1f);
        assertEquals("common", choose(weights, "common", 1000, 0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, 1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void inactiveOrInvalidBoostLeavesSelectionsAndAwardsAlone(double multiplier) {
        assertEquals("common", choose(WEIGHTS, "common", multiplier, 0));
        assertEquals(100, EffectMath.boostExperience(100, 1_000, multiplier));
        // No active effect means even an existing overflowing award is not rewritten.
        assertEquals(100, EffectMath.boostExperience(100, Integer.MAX_VALUE, multiplier));
    }

    @ParameterizedTest
    @CsvSource({"0,1", "-1,1", "1,0", "1,1", "2,1"})
    void impossibleBaselineDoesNotPromote(double targetWeight, double totalWeight) {
        assertEquals(0, EffectMath.promotionProbability(targetWeight, totalWeight, 8));
    }

    @ParameterizedTest
    @CsvSource({"100,2,200", "3,1.5,5", "1,1.1,1", "7,1.25,9", "1,1000,1000"})
    void scalesExperienceAndRoundsToNearest(int experience, double multiplier, int expected) {
        assertEquals(expected, EffectMath.boostExperience(experience, 12_345, multiplier));
    }

    @Test
    void nonPositiveExperienceIsNotTurnedIntoAReward() {
        assertEquals(0, EffectMath.boostExperience(0, 1_000, 8));
        assertEquals(-1, EffectMath.boostExperience(-1, 1_000, 8));
    }

    @Test
    void experienceAwardSaturatesBeforeCobblemonsIntAddition() {
        int current = Integer.MAX_VALUE - 150;
        int boosted = EffectMath.boostExperience(100, current, 8);
        assertEquals(150, boosted);
        assertEquals(Integer.MAX_VALUE, Math.addExact(current, boosted));
        assertEquals(0, EffectMath.boostExperience(100, Integer.MAX_VALUE, 8));
    }

    @Test
    void hugeProductCannotWrapOrBecomeNegative() {
        int boosted = EffectMath.boostExperience(Integer.MAX_VALUE, 1_000, Double.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE - 1_000, boosted);
        assertFalse(boosted < 0);
        assertEquals(Integer.MAX_VALUE, Math.addExact(1_000, boosted));
    }

    @Test
    void negativeExistingExperienceDoesNotIncreaseTheAllowedAwardBeyondIntRange() {
        assertEquals(Integer.MAX_VALUE, EffectMath.boostExperience(Integer.MAX_VALUE, -100, 8));
    }

    private static String choose(Map<String, Float> weights, String selected, double multiplier, double roll) {
        return EffectMath.boostBucket(weights, selected, TARGET::equals, multiplier, () -> roll);
    }
}
