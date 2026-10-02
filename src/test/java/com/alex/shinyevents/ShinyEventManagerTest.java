package com.alex.shinyevents;

import com.alex.shinyevents.core.ActiveEvent;
import com.alex.shinyevents.core.EventType;
import com.alex.shinyevents.core.EventSnapshot;
import com.alex.shinyevents.core.RandomizationState;
import com.alex.shinyevents.storage.EventStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ShinyEventManagerTest {
    @TempDir Path directory;
    private final AtomicLong now = new AtomicLong(1_000);
    private final FakeDisplay display = new FakeDisplay();

    private ShinyEventManager manager() {
        return new ShinyEventManager(new EventStateStore(directory.resolve("events.json")), display, now::get);
    }

    @Test
    void simultaneousEventsHaveIndependentBoostsBarsAndDeadlines() throws IOException {
        var manager = manager();
        manager.start(EventType.SHINY, 8, 10_000);
        manager.start(EventType.POKEMONXP, 2, 20_000);
        manager.start(EventType.BERRIES, 3, 30_000);
        manager.start(EventType.ULTRARARE, 2, 40_000);
        assertEquals(4, display.bars.size());
        now.set(11_000);
        // Effects expire even before the following display tick.
        assertEquals(1, manager.multiplier(EventType.SHINY));
        assertEquals(2, manager.multiplier(EventType.POKEMONXP));
        manager.tick();
        assertFalse(display.bars.containsKey(EventType.SHINY));
        assertEquals(3, display.bars.size());
        assertEquals(0.5f, display.progress.get(EventType.POKEMONXP));
        var persisted = new EventStateStore(directory.resolve("events.json")).load().events();
        assertEquals(3, persisted.size());
        assertFalse(persisted.containsKey(EventType.SHINY));
    }

    @Test
    void stoppingOneTypePreservesOtherEventsAndStopAllRemovesEverything() throws IOException {
        var manager = manager();
        manager.start(EventType.SHINY, 8, 60_000);
        manager.start(EventType.POKEMONXP, 2, 120_000);
        long xpDeadline = manager.activeEvent(EventType.POKEMONXP).endsAtEpochMillis();
        manager.stop(EventType.SHINY);
        assertEquals(1, manager.multiplier(EventType.SHINY));
        assertEquals(xpDeadline, manager.activeEvent(EventType.POKEMONXP).endsAtEpochMillis());
        assertEquals(1, display.bars.size());
        manager.stopAll();
        assertTrue(display.bars.isEmpty());
        assertTrue(new EventStateStore(directory.resolve("events.json")).load().events().isEmpty());
        for (var type : EventType.values()) assertEquals(1, manager.multiplier(type));
    }

    @Test
    void duplicateStartDoesNotChangeEitherActiveEvent() throws IOException {
        var manager = manager();
        manager.start(EventType.SHINY, 8, 60_000);
        manager.start(EventType.POKEMONXP, 2, 60_000);
        var before = new EventStateStore(directory.resolve("events.json")).load();
        assertThrows(IllegalStateException.class, () -> manager.start(EventType.SHINY, 4, 120_000));
        assertEquals(before, new EventStateStore(directory.resolve("events.json")).load());
    }

    @Test
    void restartRestoresRemainingEventsWithoutResettingDeadlines() throws IOException {
        var first = manager();
        first.start(EventType.SHINY, 8, 10_000);
        first.start(EventType.POKEMONXP, 2, 60_000);
        first.close();
        assertTrue(display.bars.isEmpty());
        assertEquals(1, first.multiplier(EventType.POKEMONXP));
        now.set(21_000);
        var restarted = manager();
        restarted.restore();
        assertNull(restarted.activeEvent(EventType.SHINY));
        assertEquals(40_000, restarted.activeEvent(EventType.POKEMONXP).remainingMillis(now.get()));
        assertEquals(1, display.bars.size());
    }

    @Test
    void expiryDuringRestoreCannotLeaveOrphanedBar() throws IOException {
        var store = new EventStateStore(directory.resolve("events.json"));
        store.save(new EventSnapshot(Map.of(EventType.SHINY, new ActiveEvent(EventType.SHINY, 8, 1_000, 2_000)), RandomizationState.disabled()));
        var reads = new AtomicLong();
        var manager = new ShinyEventManager(store, display, () -> reads.getAndIncrement() == 0 ? 1_999 : 2_000);
        manager.restore();
        assertTrue(display.bars.isEmpty());
        assertNull(manager.activeEvent(EventType.SHINY));
        assertTrue(store.load().events().isEmpty());
    }

    @Test
    void failedStartDoesNotActivateBoostOrDisplayBar() throws IOException {
        Path state = directory.resolve("events.json");
        Files.createDirectory(state);
        Files.writeString(state.resolve("blocker"), "occupied");
        var manager = manager();
        assertThrows(IOException.class, () -> manager.start(EventType.POKEMONXP, 2, 60_000));
        assertEquals(1, manager.multiplier(EventType.POKEMONXP));
        assertTrue(display.bars.isEmpty());
    }

    @Test
    void failedStopPreservesBothBoostAndBar() throws IOException {
        var manager = manager();
        manager.start(EventType.POKEMONXP, 2, 60_000);
        Path state = directory.resolve("events.json");
        Files.delete(state);
        Files.createDirectory(state);
        Files.writeString(state.resolve("blocker"), "occupied");
        assertThrows(IOException.class, () -> manager.stop(EventType.POKEMONXP));
        assertEquals(2, manager.multiplier(EventType.POKEMONXP));
        assertTrue(display.bars.containsKey(EventType.POKEMONXP));
    }

    @Test
    void invalidBerryMultiplierCannotAffectOtherEvent() throws IOException {
        var manager = manager();
        manager.start(EventType.SHINY, 8, 60_000);
        assertThrows(IllegalArgumentException.class, () -> manager.start(EventType.BERRIES, 65, 60_000));
        assertEquals(8, manager.multiplier(EventType.SHINY));
        assertEquals(1, manager.multiplier(EventType.BERRIES));
        assertEquals(1, display.bars.size());
    }

    private static final class FakeDisplay implements ShinyEventManager.Display {
        final Map<EventType, ActiveEvent> bars = new EnumMap<>(EventType.class);
        final Map<EventType, Float> progress = new EnumMap<>(EventType.class);
        public void show(ActiveEvent event, long now) { bars.put(event.type(), event); update(event, now); }
        public void update(ActiveEvent event, long now) {
            if (bars.containsKey(event.type())) progress.put(event.type(), event.progress(now));
        }
        public void hide(EventType type) { bars.remove(type); progress.remove(type); }
        public void announce(String message) {}
    }
}
