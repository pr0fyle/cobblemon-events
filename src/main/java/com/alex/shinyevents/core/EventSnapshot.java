package com.alex.shinyevents.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** One immutable transaction containing active events and random scheduling state. */
public record EventSnapshot(Map<EventType, ActiveEvent> events, RandomizationState randomization) {
    public EventSnapshot {
        if (events == null || randomization == null) {
            throw new IllegalArgumentException("Events and randomization state are required.");
        }
        EnumMap<EventType, ActiveEvent> copy = new EnumMap<>(EventType.class);
        for (Map.Entry<EventType, ActiveEvent> entry : events.entrySet()) {
            ActiveEvent event = entry.getValue();
            if (event == null || entry.getKey() != event.type()) {
                throw new IllegalArgumentException("Event map key must match its event type.");
            }
            copy.put(entry.getKey(), event);
        }
        events = Collections.unmodifiableMap(copy);
    }

    public static EventSnapshot empty() {
        return new EventSnapshot(Map.of(), RandomizationState.disabled());
    }
}
