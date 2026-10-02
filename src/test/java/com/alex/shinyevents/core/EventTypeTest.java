package com.alex.shinyevents.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventTypeTest {
    @Test
    void allTypesHaveStableIdentifiersAndLabels() {
        assertEquals(EventType.SHINY, EventType.fromId("shiny"));
        assertEquals(EventType.ULTRARARE, EventType.fromId("ultrarare"));
        assertEquals(EventType.POKEMONXP, EventType.fromId("pokemonxp"));
        assertEquals(EventType.BERRIES, EventType.fromId("berries"));
        for (EventType type : EventType.values()) {
            assertEquals(type, EventType.fromId(type.id()));
            assertFalse(type.displayName().isBlank());
        }
        assertEquals(EventType.POKEMONXP, EventType.fromId(" POKEMONXP "));
    }

    @Test
    void rejectsMissingOrUnknownTypes() {
        for (String id : new String[]{null, "", " ", "playerxp", "shinyx", "rare"}) {
            assertThrows(IllegalArgumentException.class, () -> EventType.fromId(id));
        }
    }
}
