package com.alex.shinyevents.core;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventSnapshotTest {
    @Test
    void copiesActiveEventsAndDoesNotExposeMutableState() {
        var event = new ActiveEvent(EventType.SHINY, 8, 1_000, 2_000);
        var source = new EnumMap<EventType, ActiveEvent>(EventType.class);
        source.put(EventType.SHINY, event);
        var snapshot = new EventSnapshot(source, RandomizationState.disabled());
        source.clear();
        assertEquals(Map.of(EventType.SHINY, event), snapshot.events());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.events().clear());
    }

    @Test
    void validatesNullFieldsAndMismatchedKeys() {
        var event = new ActiveEvent(EventType.SHINY, 8, 1_000, 2_000);
        var settings = RandomizationState.disabled();
        assertThrows(IllegalArgumentException.class, () -> new EventSnapshot(null, settings));
        assertThrows(IllegalArgumentException.class, () -> new EventSnapshot(Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new EventSnapshot(Map.of(EventType.BERRIES, event), settings));
        var nullValue = new HashMap<EventType, ActiveEvent>();
        nullValue.put(EventType.SHINY, null);
        assertThrows(IllegalArgumentException.class, () -> new EventSnapshot(nullValue, settings));
        var nullKey = new HashMap<EventType, ActiveEvent>();
        nullKey.put(null, event);
        assertThrows(IllegalArgumentException.class, () -> new EventSnapshot(nullKey, settings));
        assertEquals(new EventSnapshot(Map.of(), settings), EventSnapshot.empty());
    }
}
