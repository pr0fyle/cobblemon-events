package com.alex.shinyevents.core;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RandomizationStateTest {
    @Test
    void disabledDefaultsDoNotScheduleAnEvent() {
        assertEquals(new RandomizationState(false, Set.of(), 0), RandomizationState.disabled());
    }

    @Test
    void copiesPoolAndDoesNotExposeMutableState() {
        EnumSet<EventType> source = EnumSet.of(EventType.SHINY, EventType.BERRIES);
        RandomizationState settings = new RandomizationState(true, source, 60_000);
        source.clear();
        assertEquals(Set.of(EventType.SHINY, EventType.BERRIES), settings.pool());
        assertThrows(UnsupportedOperationException.class, () -> settings.pool().clear());
        assertEquals(60_000, settings.nextStartEpochMillis());
    }

    @Test
    void disabledStateCanRememberPoolAndDeadline() {
        RandomizationState settings = new RandomizationState(false, Set.of(EventType.POKEMONXP), 123_000);
        assertFalse(settings.enabled());
        assertEquals(Set.of(EventType.POKEMONXP), settings.pool());
        assertEquals(123_000, settings.nextStartEpochMillis());
        assertEquals(123_000, new RandomizationState(false, Set.of(), 123_000).nextStartEpochMillis());
    }

    @Test
    void rejectsMissingTypesEmptyEnabledPoolAndNegativeDeadline() {
        assertThrows(IllegalArgumentException.class, () -> new RandomizationState(true, Set.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new RandomizationState(false, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new RandomizationState(false, Set.of(), -1));
        Set<EventType> withNull = new HashSet<>();
        withNull.add(EventType.SHINY);
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> new RandomizationState(true, withNull, 0));
    }
}
