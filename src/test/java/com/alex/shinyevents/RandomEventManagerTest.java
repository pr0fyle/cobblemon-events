package com.alex.shinyevents;

import com.alex.shinyevents.core.ActiveEvent;
import com.alex.shinyevents.core.EventSnapshot;
import com.alex.shinyevents.core.EventType;
import com.alex.shinyevents.storage.EventStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RandomEventManagerTest {
    @TempDir Path directory;
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final FakeDisplay display = new FakeDisplay();
    private final CountingRandom random = new CountingRandom();

    private EventStateStore store() { return new EventStateStore(directory.resolve("events.json")); }
    private EventSnapshot saved() throws IOException { return store().load(); }
    private ShinyEventManager manager() { return new ShinyEventManager(store(), display, now::get, random); }

    @Test
    void firstEnableStartsOnlySelectedTypeAndUsesStartToStartSpacing() throws IOException {
        var manager = manager();
        manager.configureRandomization(Set.of(EventType.POKEMONXP));
        assertTrue(saved().randomization().enabled());
        assertEquals(0, display.sounds);
        manager.tick();
        var first = manager.activeEvent(EventType.POKEMONXP);
        assertNotNull(first);
        assertTrue(first.multiplier() >= 3 && first.multiplier() <= 10);
        assertEquals(1, display.sounds);
        assertEquals(1, display.bars.size());
        long duration = first.endsAtEpochMillis() - first.startedAtEpochMillis();
        assertTrue(duration >= 5 * 60_000 && duration <= 20 * 60_000);
        long next = saved().randomization().nextStartEpochMillis();
        assertTrue(next - first.startedAtEpochMillis() >= 60 * 60_000);
        assertTrue(next - first.startedAtEpochMillis() <= 150 * 60_000);
        now.set(next - 1);
        manager.tick();
        assertTrue(display.bars.isEmpty());
        assertEquals(1, display.sounds);
        now.set(next);
        manager.tick();
        assertEquals(next, manager.activeEvent(EventType.POKEMONXP).startedAtEpochMillis());
        assertEquals(2, display.sounds);
    }

    @Test
    void changingPoolKeepsDeadlineAndRunningEvent() throws IOException {
        var manager = manager();
        manager.configureRandomization(Set.of(EventType.POKEMONXP));
        manager.tick();
        var existing = manager.activeEvent(EventType.POKEMONXP);
        long next = saved().randomization().nextStartEpochMillis();
        manager.configureRandomization(Set.of(EventType.SHINY));
        assertEquals(next, saved().randomization().nextStartEpochMillis());
        assertEquals(existing, manager.activeEvent(EventType.POKEMONXP));
        now.set(next);
        manager.tick();
        assertNotNull(manager.activeEvent(EventType.SHINY));
        assertNull(manager.activeEvent(EventType.POKEMONXP));
        assertEquals(2, display.sounds);
    }

    @Test
    void offAndOnPreservePoolAndCooldownWithoutStoppingCurrentEvent() throws IOException {
        var manager = manager();
        manager.configureRandomization(Set.of(EventType.BERRIES));
        manager.tick();
        long next = saved().randomization().nextStartEpochMillis();
        manager.disableRandomization();
        assertNotNull(manager.activeEvent(EventType.BERRIES));
        assertFalse(saved().randomization().enabled());
        manager.enableRandomization();
        assertEquals(Set.of(EventType.BERRIES), saved().randomization().pool());
        assertEquals(next, saved().randomization().nextStartEpochMillis());
        manager.stopAll();
        manager.tick();
        assertEquals(1, display.sounds);
        assertTrue(display.bars.isEmpty());
        now.set(next);
        manager.tick();
        assertNotNull(manager.activeEvent(EventType.BERRIES));
    }

    @Test
    void disabledSchedulerDoesNotStartOverdueEventsAndSurvivesRestart() throws IOException {
        var manager = manager();
        manager.configureRandomization(Set.of(EventType.ULTRARARE));
        manager.tick();
        manager.disableRandomization();
        manager.stopAll();
        manager.close();
        now.addAndGet(10 * 60 * 60_000);
        var restarted = manager();
        restarted.restore();
        restarted.tick();
        assertEquals(1, display.sounds);
        assertTrue(display.bars.isEmpty());
        assertFalse(saved().randomization().enabled());
        assertEquals(Set.of(EventType.ULTRARARE), saved().randomization().pool());
    }

    @Test
    void overdueRestartStartsOneNewEventAndDoesNotReplayMissedEvents() throws IOException {
        var first = manager();
        first.configureRandomization(Set.of(EventType.SHINY));
        first.tick();
        first.close();
        now.addAndGet(24 * 60 * 60_000L);
        var restarted = manager();
        restarted.restore();
        assertEquals(1, display.sounds);
        assertTrue(display.bars.isEmpty());
        restarted.tick();
        assertEquals(2, display.sounds);
        assertEquals(now.get(), restarted.activeEvent(EventType.SHINY).startedAtEpochMillis());
        restarted.tick();
        assertEquals(2, display.sounds);
        assertTrue(saved().randomization().nextStartEpochMillis() >= now.get() + 60 * 60_000);
    }

    @Test
    void manualStartReschedulesAndAutomaticEventsWaitForActiveManualEvent() throws IOException {
        var manager = manager();
        manager.configureRandomization(Set.of(EventType.POKEMONXP));
        manager.start(EventType.SHINY, 8, 4 * 60 * 60_000);
        long next = saved().randomization().nextStartEpochMillis();
        assertTrue(next >= now.get() + 60 * 60_000);
        now.set(next);
        manager.tick();
        assertNull(manager.activeEvent(EventType.POKEMONXP));
        assertEquals(1, display.sounds);
        manager.stop(EventType.SHINY);
        manager.tick();
        assertNotNull(manager.activeEvent(EventType.POKEMONXP));
        assertEquals(2, display.sounds);
    }

    @Test
    void defaultOnSelectsAllAndStatusDescribesSettings() throws IOException {
        var manager = manager();
        manager.enableRandomization();
        assertEquals(Set.of(EventType.values()), saved().randomization().pool());
        assertTrue(manager.randomizationStatus().contains("ON"));
        assertTrue(manager.randomizationStatus().contains("60-150"));
        manager.disableRandomization();
        assertTrue(manager.randomizationStatus().contains("OFF"));
    }

    @Test
    void failedAutomaticSaveDoesNotStartOrSoundAndIsThrottled() throws IOException {
        var manager = manager();
        manager.configureRandomization(Set.of(EventType.SHINY));
        Path state = directory.resolve("events.json");
        Files.delete(state);
        Files.createDirectory(state);
        Files.writeString(state.resolve("occupied"), "block writes");
        manager.tick();
        int calls = random.calls;
        assertTrue(display.bars.isEmpty());
        assertEquals(0, display.sounds);
        assertTrue(manager.randomizationStatus().contains("01:00"));
        now.addAndGet(1_000);
        manager.tick();
        assertEquals(calls, random.calls);
        now.addAndGet(60_000);
        manager.tick();
        assertTrue(random.calls > calls);
        assertEquals(0, display.sounds);
        Files.delete(state.resolve("occupied"));
        Files.delete(state);
        now.addAndGet(60_000);
        manager.tick();
        assertEquals(1, display.sounds);
        assertNotNull(manager.activeEvent(EventType.SHINY));
        assertTrue(saved().randomization().nextStartEpochMillis() >= now.get() + 60 * 60_000);
        manager.tick();
        assertEquals(1, display.sounds);
    }

    @Test
    void soundsOnlyOnSuccessfulNewStartsNeverOnRestoreJoinStopOrExpiry() throws IOException {
        var first = manager();
        first.start(EventType.SHINY, 8, 60_000);
        assertEquals(1, display.sounds);
        assertThrows(IllegalStateException.class, () -> first.start(EventType.SHINY, 8, 60_000));
        first.onJoin(null);
        assertEquals(1, display.sounds);
        first.close();
        var restarted = manager();
        restarted.restore();
        restarted.onJoin(null);
        assertEquals(1, display.sounds);
        now.addAndGet(60_000);
        restarted.tick();
        assertEquals(1, display.sounds);
        restarted.start(EventType.BERRIES, 5, 60_000);
        assertEquals(2, display.sounds);
        restarted.stopAll();
        assertEquals(2, display.sounds);
    }

    private static final class CountingRandom extends Random {
        int calls;
        CountingRandom() { super(94721); }
        @Override public int nextInt(int origin, int bound) { calls++; return super.nextInt(origin, bound); }
        @Override public int nextInt(int bound) { calls++; return super.nextInt(bound); }
    }

    private static final class FakeDisplay implements ShinyEventManager.Display {
        final Map<EventType, ActiveEvent> bars = new EnumMap<>(EventType.class);
        int sounds;
        public void show(ActiveEvent event, long now) { bars.put(event.type(), event); }
        public void update(ActiveEvent event, long now) {}
        public void hide(EventType type) { bars.remove(type); }
        public void announce(String message) {}
        public void startSound() { sounds++; }
    }
}
