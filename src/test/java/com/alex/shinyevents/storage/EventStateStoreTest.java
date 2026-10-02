package com.alex.shinyevents.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alex.shinyevents.core.ActiveEvent;
import com.alex.shinyevents.core.EventType;
import com.alex.shinyevents.core.EventSnapshot;
import com.alex.shinyevents.core.RandomizationState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventStateStoreTest {
    @TempDir
    Path directory;

    @Test
    void missingFileMeansNoEvent() throws IOException {
        assertEquals(EventSnapshot.empty(), new EventStateStore(directory.resolve("absent/state.json")).load());
    }

    @Test
    void roundTripsIntoANewStoreInstanceAndCreatesParentDirectories() throws IOException {
        Path stateFile = directory.resolve("world/data/shiny-events.json");
        ActiveEvent event = new ActiveEvent(EventType.SHINY, 8.0, 1_800_000_000_000L, 1_800_000_900_000L);

        new EventStateStore(stateFile).save(snapshot(Map.of(EventType.SHINY, event)));

        assertEquals(Map.of(EventType.SHINY, event), new EventStateStore(stateFile).load().events());
    }

    @Test
    void replacesPreviousEventAndLeavesNoTemporaryFiles() throws IOException {
        Path stateFile = directory.resolve("state.json");
        EventStateStore store = new EventStateStore(stateFile);
        store.save(snapshot(Map.of(EventType.SHINY, new ActiveEvent(EventType.SHINY, 2.0, 1_000L, 61_000L))));
        ActiveEvent replacement = new ActiveEvent(EventType.SHINY, 8.0, 2_000L, 902_000L);

        store.save(snapshot(Map.of(EventType.SHINY, replacement)));

        assertEquals(Map.of(EventType.SHINY, replacement), store.load().events());
        try (var files = Files.list(directory)) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    void clearRemovesSavedEventAndIsSafeWhenAlreadyAbsent() throws IOException {
        Path stateFile = directory.resolve("state.json");
        EventStateStore store = new EventStateStore(stateFile);
        store.save(snapshot(Map.of(EventType.SHINY, new ActiveEvent(EventType.SHINY, 8.0, 1_000L, 901_000L))));
        assertTrue(Files.exists(stateFile));

        store.clear();
        store.clear();

        assertFalse(Files.exists(stateFile));
        assertEquals(Map.of(), store.load().events());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "{", "null", "[]", "{}", "true",
        "{schemaVersion:1,multiplier:8,startedAtEpochMillis:1000,endsAtEpochMillis:61000}",
        "{\"schemaVersion\":1,\"multiplier\":8,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":61000} {}",
        "{\"schemaVersion\":1,\"multiplier\":\"8\",\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":61000}",
        "{\"schemaVersion\":2,\"multiplier\":8,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":61000}",
        "{\"schemaVersion\":1,\"multiplier\":8,\"startedAtEpochMillis\":1.5,\"endsAtEpochMillis\":61000}",
        "{\"schemaVersion\":1,\"multiplier\":8,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":9223372036854775808}"
    })
    void rejectsMalformedOrUnsupportedStateWithoutDeletingIt(String json) throws IOException {
        Path stateFile = directory.resolve("state.json");
        Files.writeString(stateFile, json);

        assertThrows(IOException.class, () -> new EventStateStore(stateFile).load().events());
        assertEquals(json, Files.readString(stateFile));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"schemaVersion\":1,\"multiplier\":1,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":61000}",
        "{\"schemaVersion\":1,\"multiplier\":0,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":61000}",
        "{\"schemaVersion\":1,\"multiplier\":-8,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":61000}",
        "{\"schemaVersion\":1,\"multiplier\":1000001,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":61000}",
        "{\"schemaVersion\":1,\"multiplier\":1e999,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":61000}",
        "{\"schemaVersion\":1,\"multiplier\":8,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":1000}",
        "{\"schemaVersion\":1,\"multiplier\":8,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":999}",
        "{\"schemaVersion\":1,\"multiplier\":8,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":604801001}"
    })
    void rejectsInvalidEventRanges(String json) throws IOException {
        Path stateFile = directory.resolve("state.json");
        Files.writeString(stateFile, json);

        assertThrows(IOException.class, () -> new EventStateStore(stateFile).load().events());
    }

    @Test
    void propagatesFileAccessErrorsInsteadOfTreatingThemAsNoEvent() throws IOException {
        Path stateFile = Files.createDirectory(directory.resolve("state.json"));

        assertThrows(IOException.class, () -> new EventStateStore(stateFile).load().events());
    }

    @Test
    void roundTripsAllSimultaneousEventsWithIndependentDeadlines() throws IOException {
        Path stateFile = directory.resolve("state.json");
        Map<EventType, ActiveEvent> events = new EnumMap<>(EventType.class);
        for (EventType type : EventType.values()) {
            events.put(type, new ActiveEvent(type, 2 + type.ordinal(),
                    1_800_000_000_000L, 1_800_000_900_000L + type.ordinal() * 60_000));
        }
        new EventStateStore(stateFile).save(snapshot(events));
        assertEquals(events, new EventStateStore(stateFile).load().events());
        assertTrue(Files.readString(stateFile).contains("\"schemaVersion\": 3"));
    }

    @Test
    void migratesLegacyShinyStateWithoutChangingItsDeadline() throws IOException {
        Path stateFile = directory.resolve("state.json");
        String legacy = """
                {"schemaVersion":1,"multiplier":8,"startedAtEpochMillis":1000,"endsAtEpochMillis":901000}
                """;
        Files.writeString(stateFile, legacy);
        EventStateStore store = new EventStateStore(stateFile);
        EventSnapshot loaded = store.load();
        assertEquals(Map.of(EventType.SHINY, new ActiveEvent(EventType.SHINY, 8, 1_000, 901_000)), loaded.events());
        assertEquals(RandomizationState.disabled(), loaded.randomization());
        // Loading preserves the source; a successful save upgrades the schema.
        assertEquals(legacy, Files.readString(stateFile));
        store.save(loaded);
        assertTrue(Files.readString(stateFile).contains("\"schemaVersion\": 3"));
        assertEquals(loaded, store.load());
    }

    @Test
    void replacingOneEventKeepsTheOtherSavedEvent() throws IOException {
        EventStateStore store = new EventStateStore(directory.resolve("state.json"));
        ActiveEvent xp = new ActiveEvent(EventType.POKEMONXP, 2, 1_000, 121_000);
        store.save(snapshot(Map.of(EventType.SHINY, new ActiveEvent(EventType.SHINY, 2, 1_000, 61_000), EventType.POKEMONXP, xp)));
        ActiveEvent replacement = new ActiveEvent(EventType.SHINY, 8, 2_000, 902_000);
        store.save(snapshot(Map.of(EventType.SHINY, replacement, EventType.POKEMONXP, xp)));
        assertEquals(Map.of(EventType.SHINY, replacement, EventType.POKEMONXP, xp), store.load().events());
    }

    @Test
    void emptySaveRemovesState() throws IOException {
        Path stateFile = directory.resolve("state.json");
        EventStateStore store = new EventStateStore(stateFile);
        store.save(snapshot(Map.of(EventType.SHINY, new ActiveEvent(EventType.SHINY, 8, 1_000, 901_000))));
        store.save(snapshot(Map.of()));
        assertFalse(Files.exists(stateFile));
        assertEquals(Map.of(), store.load().events());
    }

    @Test
    void emptyEventsArrayIsValid() throws IOException {
        Path stateFile = directory.resolve("state.json");
        Files.writeString(stateFile, "{\"schemaVersion\":2,\"events\":[]}");
        assertEquals(Map.of(), new EventStateStore(stateFile).load().events());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"schemaVersion\":4,\"events\":[]}",
        "{\"schemaVersion\":3,\"events\":[]}",
        "{\"schemaVersion\":2,\"events\":null}",
        "{\"schemaVersion\":2,\"events\":{}}",
        "{\"schemaVersion\":2,\"events\":[null]}",
        "{\"schemaVersion\":2,\"events\":[{}]}",
        "{\"schemaVersion\":2,\"events\":[{\"type\":1,\"multiplier\":2,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":2000}]}",
        "{\"schemaVersion\":2,\"events\":[{\"type\":\"rare\",\"multiplier\":2,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":2000}]}",
        "{\"schemaVersion\":2,\"events\":[{\"type\":\"berries\",\"multiplier\":65,\"startedAtEpochMillis\":1000,\"endsAtEpochMillis\":2000}]}"
    })
    void rejectsMalformedOrUnsupportedMultiEventState(String json) throws IOException {
        Path stateFile = directory.resolve("state.json");
        Files.writeString(stateFile, json);
        assertThrows(IOException.class, () -> new EventStateStore(stateFile).load().events());
        assertEquals(json, Files.readString(stateFile));
    }

    @Test
    void rejectsDuplicateTypesEvenWithDifferentCapitalization() throws IOException {
        Path stateFile = directory.resolve("state.json");
        String json = """
                {"schemaVersion":2,"events":[
                  {"type":"shiny","multiplier":8,"startedAtEpochMillis":1000,"endsAtEpochMillis":2000},
                  {"type":"SHINY","multiplier":2,"startedAtEpochMillis":2000,"endsAtEpochMillis":3000}
                ]}
                """;
        Files.writeString(stateFile, json);
        assertThrows(IOException.class, () -> new EventStateStore(stateFile).load().events());
        assertEquals(json, Files.readString(stateFile));
    }

    @Test
    void corruptionAfterAValidEventFailsTheWholeLoad() throws IOException {
        Path stateFile = directory.resolve("state.json");
        Files.writeString(stateFile, """
                {"schemaVersion":2,"events":[
                  {"type":"shiny","multiplier":8,"startedAtEpochMillis":1000,"endsAtEpochMillis":2000},
                  {"type":"pokemonxp","multiplier":2,"startedAtEpochMillis":1000}
                ]}
                """);
        assertThrows(IOException.class, () -> new EventStateStore(stateFile).load().events());
    }

    @Test
    void refusesMismatchedMapKeysBeforeTouchingSavedState() throws IOException {
        Path stateFile = directory.resolve("state.json");
        EventStateStore store = new EventStateStore(stateFile);
        ActiveEvent event = new ActiveEvent(EventType.SHINY, 8, 1_000, 61_000);
        store.save(snapshot(Map.of(EventType.SHINY, event)));
        String saved = Files.readString(stateFile);
        assertThrows(IllegalArgumentException.class, () -> store.save(snapshot(Map.of(EventType.BERRIES, event))));
        assertEquals(saved, Files.readString(stateFile));
    }

    @Test
    void migratesVersionTwoWithRandomizationDisabled() throws IOException {
        Path stateFile = directory.resolve("state.json");
        String legacy = """
                {"schemaVersion":2,"events":[
                  {"type":"shiny","multiplier":8,"startedAtEpochMillis":1000,"endsAtEpochMillis":2000},
                  {"type":"pokemonxp","multiplier":2,"startedAtEpochMillis":1000,"endsAtEpochMillis":3000}
                ]}
                """;
        Files.writeString(stateFile, legacy);
        EventStateStore store = new EventStateStore(stateFile);
        EventSnapshot loaded = store.load();
        assertEquals(RandomizationState.disabled(), loaded.randomization());
        assertEquals(Map.of(EventType.SHINY, new ActiveEvent(EventType.SHINY, 8, 1_000, 2_000),
                EventType.POKEMONXP, new ActiveEvent(EventType.POKEMONXP, 2, 1_000, 3_000)), loaded.events());
        assertEquals(legacy, Files.readString(stateFile));
        store.save(loaded);
        assertTrue(Files.readString(stateFile).contains("\"schemaVersion\": 3"));
        assertEquals(loaded, store.load());
    }

    @Test
    void roundTripsEventsAndFullRandomizationStateTogether() throws IOException {
        Path stateFile = directory.resolve("state.json");
        EventSnapshot snapshot = new EventSnapshot(
                Map.of(EventType.BERRIES, new ActiveEvent(EventType.BERRIES, 20, 1_000, 301_000)),
                new RandomizationState(true, EnumSet.allOf(EventType.class), 9_001_000));
        new EventStateStore(stateFile).save(snapshot);
        assertEquals(snapshot, new EventStateStore(stateFile).load());
    }

    @Test
    void retainsDisabledPreferencesOrDeadlineEvenWithNoActiveEvents() throws IOException {
        Path stateFile = directory.resolve("state.json");
        EventStateStore store = new EventStateStore(stateFile);
        for (RandomizationState settings : new RandomizationState[]{
                new RandomizationState(false, Set.of(EventType.SHINY, EventType.POKEMONXP), 9_001_000),
                new RandomizationState(false, Set.of(EventType.BERRIES), 0),
                new RandomizationState(false, Set.of(), 9_001_000)}) {
            EventSnapshot snapshot = new EventSnapshot(Map.of(), settings);
            store.save(snapshot);
            assertTrue(Files.exists(stateFile));
            assertEquals(snapshot, store.load());
        }
        store.clear();
        assertEquals(EventSnapshot.empty(), store.load());
    }

    @Test
    void preservesEnabledScheduleWhileNoEventIsRunning() throws IOException {
        EventStateStore store = new EventStateStore(directory.resolve("state.json"));
        EventSnapshot snapshot = new EventSnapshot(Map.of(),
                new RandomizationState(true, Set.of(EventType.ULTRARARE), 1_800_000_000_000L));
        store.save(snapshot);
        assertEquals(snapshot, store.load());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null", "[]", "{}",
        "{\"enabled\":\"true\",\"pool\":[\"shiny\"],\"nextStartEpochMillis\":0}",
        "{\"enabled\":1,\"pool\":[\"shiny\"],\"nextStartEpochMillis\":0}",
        "{\"enabled\":null,\"pool\":[\"shiny\"],\"nextStartEpochMillis\":0}",
        "{\"enabled\":true,\"pool\":[],\"nextStartEpochMillis\":0}",
        "{\"enabled\":false,\"nextStartEpochMillis\":0}",
        "{\"enabled\":false,\"pool\":null,\"nextStartEpochMillis\":0}",
        "{\"enabled\":false,\"pool\":\"shiny\",\"nextStartEpochMillis\":0}",
        "{\"enabled\":true,\"pool\":[1],\"nextStartEpochMillis\":0}",
        "{\"enabled\":true,\"pool\":[null],\"nextStartEpochMillis\":0}",
        "{\"enabled\":true,\"pool\":[\"rare\"],\"nextStartEpochMillis\":0}",
        "{\"enabled\":true,\"pool\":[\"shiny\",\"SHINY\"],\"nextStartEpochMillis\":0}",
        "{\"enabled\":true,\"pool\":[\"shiny\"]}",
        "{\"enabled\":false,\"pool\":[],\"nextStartEpochMillis\":-1}",
        "{\"enabled\":true,\"pool\":[\"shiny\"],\"nextStartEpochMillis\":1.5}",
        "{\"enabled\":true,\"pool\":[\"shiny\"],\"nextStartEpochMillis\":\"1000\"}",
        "{\"enabled\":true,\"pool\":[\"shiny\"],\"nextStartEpochMillis\":9223372036854775808}"
    })
    void invalidRandomizationRejectsTheEntireSnapshotWithoutDeletingState(String randomization) throws IOException {
        Path stateFile = directory.resolve("state.json");
        String json = """
                {"schemaVersion":3,"events":[
                  {"type":"shiny","multiplier":8,"startedAtEpochMillis":1000,"endsAtEpochMillis":2000}
                ],"randomization":%s}
                """.formatted(randomization);
        Files.writeString(stateFile, json);
        assertThrows(IOException.class, () -> new EventStateStore(stateFile).load());
        assertEquals(json, Files.readString(stateFile));
    }

    private static EventSnapshot snapshot(Map<EventType, ActiveEvent> events) {
        return new EventSnapshot(events, RandomizationState.disabled());
    }
}
