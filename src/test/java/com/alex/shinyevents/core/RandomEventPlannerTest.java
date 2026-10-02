package com.alex.shinyevents.core;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RandomEventPlannerTest {
    @Test
    void eachTypeUsesItsRequestedInclusiveMultiplierAndDurationEndpoints() {
        int[] minima = {4, 2, 3, 5};
        int[] maxima = {32, 4, 10, 20};
        for (EventType type : EventType.values()) {
            ActiveEvent smallest = RandomEventPlanner.chooseEvent(Set.of(type), 12_345, new BoundaryRandom(false, 0));
            ActiveEvent largest = RandomEventPlanner.chooseEvent(Set.of(type), 12_345, new BoundaryRandom(true, 0));
            assertEquals(type, smallest.type());
            assertEquals(type, largest.type());
            assertEquals(minima[type.ordinal()], smallest.multiplier());
            assertEquals(maxima[type.ordinal()], largest.multiplier());
            assertEquals(12_345, smallest.startedAtEpochMillis());
            assertEquals(12_345 + 5 * 60_000L, smallest.endsAtEpochMillis());
            assertEquals(12_345 + 20 * 60_000L, largest.endsAtEpochMillis());
        }
    }

    @Test
    void eachPoolEntryHasExactlyOneSelectionIndexAndExcludedTypesCannotBeChosen() {
        Set<EventType> pool = Set.of(EventType.POKEMONXP, EventType.BERRIES, EventType.SHINY);
        EventType[] ordered = {EventType.SHINY, EventType.POKEMONXP, EventType.BERRIES};
        for (int index = 0; index < ordered.length; index++) {
            BoundaryRandom random = new BoundaryRandom(false, index);
            assertEquals(ordered[index], RandomEventPlanner.chooseEvent(pool, 0, random).type());
            assertEquals(3, random.selectionBound);
        }
    }

    @Test
    void intervalBetweenStartsIncludesBothSixtyAndOneHundredFiftyMinutes() {
        assertEquals(60 * 60_000L, RandomEventPlanner.randomStartIntervalMillis(new BoundaryRandom(false, 0)));
        assertEquals(150 * 60_000L, RandomEventPlanner.randomStartIntervalMillis(new BoundaryRandom(true, 0)));
    }

    @Test
    void seededSelectionsStayWithinRequestedWholeNumberRanges() {
        Random random = new Random(2_026);
        Set<EventType> seen = EnumSet.noneOf(EventType.class);
        for (int i = 0; i < 1_000; i++) {
            ActiveEvent event = RandomEventPlanner.chooseEvent(EnumSet.allOf(EventType.class), 50_000, random);
            seen.add(event.type());
            assertEquals(Math.rint(event.multiplier()), event.multiplier());
            assertTrue(event.multiplier() >= RandomEventPlanner.minimumMultiplier(event.type()));
            assertTrue(event.multiplier() <= RandomEventPlanner.maximumMultiplier(event.type()));
            long duration = event.endsAtEpochMillis() - event.startedAtEpochMillis();
            assertEquals(0, duration % 60_000);
            assertTrue(duration >= 5 * 60_000L && duration <= 20 * 60_000L);
            long interval = RandomEventPlanner.randomStartIntervalMillis(random);
            assertEquals(0, interval % 60_000);
            assertTrue(interval >= 60 * 60_000L && interval <= 150 * 60_000L);
        }
        assertEquals(EnumSet.allOf(EventType.class), seen);
    }

    @Test
    void rejectsInvalidPoolAndDeadlineOverflow() {
        assertThrows(IllegalArgumentException.class, () -> RandomEventPlanner.chooseEvent(Set.of(), 0, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> RandomEventPlanner.chooseEvent(null, 0, new Random(1)));
        Set<EventType> withNull = new HashSet<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> RandomEventPlanner.chooseEvent(withNull, 0, new Random(1)));
        assertThrows(IllegalArgumentException.class,
                () -> RandomEventPlanner.chooseEvent(Set.of(EventType.SHINY), Long.MAX_VALUE, new Random(1)));
    }

    private static final class BoundaryRandom implements RandomGenerator {
        private final boolean upper;
        private final int selection;
        private int selectionBound;

        BoundaryRandom(boolean upper, int selection) {
            this.upper = upper;
            this.selection = selection;
        }

        @Override
        public long nextLong() {
            throw new AssertionError("Planner must use bounded integer draws.");
        }

        @Override
        public int nextInt(int bound) {
            selectionBound = bound;
            assertTrue(selection >= 0 && selection < bound);
            return selection;
        }

        @Override
        public int nextInt(int origin, int bound) {
            return upper ? bound - 1 : origin;
        }
    }
}
