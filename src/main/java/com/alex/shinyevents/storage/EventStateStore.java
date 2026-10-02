package com.alex.shinyevents.storage;

import com.alex.shinyevents.core.ActiveEvent;
import com.alex.shinyevents.core.EventType;
import com.alex.shinyevents.core.EventSnapshot;
import com.alex.shinyevents.core.RandomizationState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/** Stores one world's events without changing Cobblemon's own configuration. */
public final class EventStateStore {
    private static final int SCHEMA_VERSION = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TypeAdapter<JsonElement> JSON_ADAPTER = GSON.getAdapter(JsonElement.class);

    private final Path stateFile;

    public EventStateStore(Path stateFile) {
        this.stateFile = Objects.requireNonNull(stateFile, "stateFile").toAbsolutePath().normalize();
    }

    /** A missing file means no saved events; corrupt or unsupported state is an error. */
    public EventSnapshot load() throws IOException {
        Reader input;
        try {
            input = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            return EventSnapshot.empty();
        }

        try (JsonReader reader = new JsonReader(input)) {
            reader.setLenient(false);
            JsonElement document = JSON_ADAPTER.read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || document == null || !document.isJsonObject()) {
                throw new IOException("Expected a single event state object in " + stateFile);
            }

            JsonObject object = document.getAsJsonObject();
            long version = requiredLong(object, "schemaVersion");
            if (version == 1) {
                // The original addon saved a single shiny event at the top level.
                ActiveEvent shiny = readEvent(object, EventType.SHINY);
                return new EventSnapshot(Map.of(EventType.SHINY, shiny), RandomizationState.disabled());
            }
            if (version != 2 && version != SCHEMA_VERSION) {
                throw new IOException("Unsupported event state version in " + stateFile);
            }
            JsonElement events = object.get("events");
            if (events == null || !events.isJsonArray()) {
                throw new IOException("Expected an events array in " + stateFile);
            }
            Map<EventType, ActiveEvent> loaded = new EnumMap<>(EventType.class);
            for (JsonElement item : events.getAsJsonArray()) {
                if (!item.isJsonObject()) {
                    throw new IOException("Expected an event object in " + stateFile);
                }
                JsonObject eventObject = item.getAsJsonObject();
                EventType type = EventType.fromId(requiredString(eventObject, "type"));
                ActiveEvent event = readEvent(eventObject, type);
                if (loaded.putIfAbsent(type, event) != null) {
                    throw new IOException("Duplicate event type " + type.id() + " in " + stateFile);
                }
            }
            RandomizationState randomization = version == 2
                    ? RandomizationState.disabled() : readRandomization(object);
            return new EventSnapshot(loaded, randomization);
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException | ArithmeticException invalid) {
            throw new IOException("Invalid event state in " + stateFile, invalid);
        }
    }

    /** Write a complete sibling file before replacing the previous saved events. */
    public void save(EventSnapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.equals(EventSnapshot.empty())) {
            clear();
            return;
        }
        JsonObject object = new JsonObject();
        object.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        for (ActiveEvent event : snapshot.events().values()) {
            JsonObject saved = new JsonObject();
            saved.addProperty("type", event.type().id());
            saved.addProperty("multiplier", event.multiplier());
            saved.addProperty("startedAtEpochMillis", event.startedAtEpochMillis());
            saved.addProperty("endsAtEpochMillis", event.endsAtEpochMillis());
            array.add(saved);
        }
        object.add("events", array);
        RandomizationState settings = snapshot.randomization();
        JsonObject randomization = new JsonObject();
        randomization.addProperty("enabled", settings.enabled());
        JsonArray pool = new JsonArray();
        for (EventType type : settings.pool()) {
            pool.add(type.id());
        }
        randomization.add("pool", pool);
        randomization.addProperty("nextStartEpochMillis", settings.nextStartEpochMillis());
        object.add("randomization", randomization);

        Path directory = stateFile.getParent();
        Files.createDirectories(directory);
        Path temporaryFile = Files.createTempFile(directory, stateFile.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temporaryFile, GSON.toJson(object) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, stateFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporaryFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    public void clear() throws IOException {
        Files.deleteIfExists(stateFile);
    }

    private static ActiveEvent readEvent(JsonObject object, EventType type) {
        double multiplier = requiredNumber(object, "multiplier").getAsDouble();
        long startedAt = requiredLong(object, "startedAtEpochMillis");
        long endsAt = requiredLong(object, "endsAtEpochMillis");
        return new ActiveEvent(type, multiplier, startedAt, endsAt);
    }

    private static RandomizationState readRandomization(JsonObject root) {
        JsonElement value = root.get("randomization");
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Expected a randomization object.");
        }
        JsonObject object = value.getAsJsonObject();
        JsonElement enabled = object.get("enabled");
        if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Randomization enabled must be a boolean.");
        }
        JsonElement savedPool = object.get("pool");
        if (savedPool == null || !savedPool.isJsonArray()) {
            throw new IllegalArgumentException("Randomization pool must be an array.");
        }
        EnumSet<EventType> pool = EnumSet.noneOf(EventType.class);
        for (JsonElement item : savedPool.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Randomization pool must contain event type names.");
            }
            EventType type = EventType.fromId(item.getAsString());
            if (!pool.add(type)) {
                throw new IllegalArgumentException("Duplicate randomization pool type: " + type.id());
            }
        }
        return new RandomizationState(enabled.getAsBoolean(), pool, requiredLong(object, "nextStartEpochMillis"));
    }

    private static String requiredString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing or non-string event field: " + field);
        }
        return value.getAsString();
    }

    private static JsonPrimitive requiredNumber(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing or non-numeric event field: " + field);
        }
        return value.getAsJsonPrimitive();
    }

    private static long requiredLong(JsonObject object, String field) {
        return requiredNumber(object, field).getAsBigDecimal().longValueExact();
    }
}
