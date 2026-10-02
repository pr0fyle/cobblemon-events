package com.alex.shinyevents;

import com.alex.shinyevents.core.ActiveEvent;
import com.alex.shinyevents.audio.EventStartSound;
import com.alex.shinyevents.core.EventSnapshot;
import com.alex.shinyevents.core.EventType;
import com.alex.shinyevents.core.RandomEventPlanner;
import com.alex.shinyevents.core.RandomizationState;
import com.alex.shinyevents.storage.EventStateStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/** Owns independent event deadlines; all writes happen on the server thread. */
final class ShinyEventManager {
    private final EventStateStore stateStore;
    private final Display display;
    private final LongSupplier clock;
    private final RandomGenerator random;
    private volatile Map<EventType, ActiveEvent> active = Map.of();
    private RandomizationState randomization = RandomizationState.disabled();
    private long retryRandomStartAt;

    ShinyEventManager(MinecraftServer server) {
        this(new EventStateStore(server.getWorldPath(LevelResource.ROOT).resolve("data/shiny-events.json")),
                new GameDisplay(server), System::currentTimeMillis);
    }

    ShinyEventManager(EventStateStore stateStore, Display display, LongSupplier clock) {
        this(stateStore, display, clock, RandomGenerator.getDefault());
    }

    ShinyEventManager(EventStateStore stateStore, Display display, LongSupplier clock, RandomGenerator random) {
        this.stateStore = stateStore;
        this.display = display;
        this.clock = clock;
        this.random = random;
    }

    void restore() {
        try {
            var saved = stateStore.load();
            var alive = new EnumMap<EventType, ActiveEvent>(EventType.class);
            long now = clock.getAsLong();
            saved.events().forEach((type, event) -> {
                if (event.isActive(now)) alive.put(type, event);
            });
            // Persist the upgrade and remove expired entries before enabling effects.
            saveState(alive, saved.randomization());
            active = Map.copyOf(alive);
            randomization = saved.randomization();
            expireEvents(clock.getAsLong());
            active.forEach((type, event) -> display.show(event, clock.getAsLong()));
        } catch (IOException exception) {
            ShinyEventsMod.LOGGER.error("Cannot restore shiny-events.json; no events were enabled. "
                    + "Check this file before starting a new event.", exception);
        }
    }

    void start(EventType type, double multiplier, long durationMillis) throws IOException {
        expireEvents(clock.getAsLong());
        if (active.containsKey(type)) throw new IllegalStateException(type.displayName()
                + " is already running. Use /eventstop " + type.id() + " first.");
        long now = clock.getAsLong();
        var event = new ActiveEvent(type, multiplier, now, Math.addExact(now, durationMillis));
        activate(event, nextRandomizationAfterStart(now));
    }

    private void activate(ActiveEvent event, RandomizationState nextRandomization) throws IOException {
        var next = mutableSnapshot();
        next.put(event.type(), event);
        // Persist the effect and its next automatic deadline in one transaction.
        saveState(next, nextRandomization);
        active = Map.copyOf(next);
        randomization = nextRandomization;
        retryRandomStartAt = 0;
        expireEvents(clock.getAsLong());
        if (activeEvent(event.type()) == null) throw new IllegalStateException(
                "The event expired while being saved. Try a longer duration.");
        display.show(event, clock.getAsLong());
        display.startSound();
        display.announce(event.type().displayName() + " started! " + multiplierLabel(event.multiplier())
                + "x for " + formatTime(event.remainingSeconds(clock.getAsLong())) + ".");
    }

    void stop(EventType type) throws IOException {
        expireEvents(clock.getAsLong());
        if (!active.containsKey(type)) throw new IllegalStateException(type.displayName() + " is not running.");
        var next = mutableSnapshot();
        next.remove(type);
        saveState(next, randomization);
        active = Map.copyOf(next);
        display.hide(type);
        display.announce(type.displayName() + " stopped. Normal rates restored for this event.");
    }

    void stopAll() throws IOException {
        expireEvents(clock.getAsLong());
        if (active.isEmpty()) throw new IllegalStateException("No events are running.");
        saveState(Map.of(), randomization);
        var previous = active;
        active = Map.of();
        previous.keySet().forEach(display::hide);
        display.announce("All events stopped. Normal rates restored.");
    }

    ActiveEvent activeEvent(EventType type) {
        var event = active.get(type);
        return event != null && event.isActive(clock.getAsLong()) ? event : null;
    }

    double multiplier(EventType type) {
        var event = activeEvent(type);
        return event == null ? 1 : event.multiplier();
    }

    void tick() {
        long now = clock.getAsLong();
        expireEvents(now);
        if (!randomization.enabled() || !active.isEmpty()
                || now < randomization.nextStartEpochMillis() || now < retryRandomStartAt) return;
        try {
            var event = RandomEventPlanner.chooseEvent(randomization.pool(), now, random);
            activate(event, nextRandomizationAfterStart(now));
        } catch (IOException | IllegalStateException exception) {
            // Avoid disk/log storms while allowing a transient failure to recover.
            retryRandomStartAt = now + 60_000;
            ShinyEventsMod.LOGGER.error("Could not start random event; retrying in one minute", exception);
        }
    }

    private void expireEvents(long now) {
        var snapshot = active;
        if (snapshot.isEmpty()) return;
        var next = new EnumMap<EventType, ActiveEvent>(EventType.class);
        snapshot.forEach((type, event) -> {
            if (event.isActive(now)) next.put(type, event);
        });
        if (next.size() != snapshot.size()) {
            active = Map.copyOf(next);
            snapshot.forEach((type, event) -> {
                if (!next.containsKey(type)) {
                    display.hide(type);
                    display.announce(type.displayName() + " ended. Normal rates restored for this event.");
                }
            });
            try {
                saveState(next, randomization);
            } catch (IOException exception) {
                // Stale deadlines are already expired and cannot revive a boost.
                ShinyEventsMod.LOGGER.error("Could not save event expiry", exception);
            }
        }
        next.values().forEach(event -> display.update(event, now));
    }

    void onJoin(ServerPlayer player) {
        expireEvents(clock.getAsLong());
        display.onJoin(player);
    }

    void onLeave(ServerPlayer player) {
        display.onLeave(player);
    }

    void close() {
        var previous = active;
        active = Map.of();
        randomization = RandomizationState.disabled();
        previous.keySet().forEach(display::hide);
        // The saved wall-clock deadlines remain intact across restarts.
    }

    void configureRandomization(Set<EventType> pool) throws IOException {
        expireEvents(clock.getAsLong());
        long nextStart = randomization.nextStartEpochMillis();
        if (nextStart == 0) {
            // Legacy saves may contain an event but no scheduler deadline yet.
            long latestStart = active.values().stream().mapToLong(ActiveEvent::startedAtEpochMillis)
                    .max().orElse(-1);
            nextStart = latestStart < 0 ? clock.getAsLong()
                    : latestStart + RandomEventPlanner.randomStartIntervalMillis(random);
        }
        var next = new RandomizationState(true, pool, nextStart);
        saveState(active, next);
        randomization = next;
        retryRandomStartAt = 0;
    }

    void enableRandomization() throws IOException {
        configureRandomization(randomization.pool().isEmpty()
                ? EnumSet.allOf(EventType.class) : randomization.pool());
    }

    void disableRandomization() throws IOException {
        var next = new RandomizationState(false, randomization.pool(), randomization.nextStartEpochMillis());
        saveState(active, next);
        randomization = next;
        retryRandomStartAt = 0;
    }

    String randomizationStatus() {
        String pool = EnumSet.allOf(EventType.class).stream().filter(randomization.pool()::contains)
                .map(EventType::id).collect(Collectors.joining(", "));
        if (!randomization.enabled()) return "Random events: OFF. Saved pool: "
                + (pool.isEmpty() ? "none (on enables all types)" : pool) + ".";
        long remaining = Math.max(0, Math.max(randomization.nextStartEpochMillis(), retryRandomStartAt)
                - clock.getAsLong());
        String next = remaining > 0 ? "in " + formatTime((remaining + 999) / 1000)
                : active.isEmpty() ? "on the next server tick" : "after the active events finish";
        return "Random events: ON. Pool: " + pool + ". Next start " + next
                + ". Starts 60-150 minutes apart; duration 5-20 minutes.";
    }

    private RandomizationState nextRandomizationAfterStart(long startedAt) {
        return new RandomizationState(randomization.enabled(), randomization.pool(),
                Math.addExact(startedAt, RandomEventPlanner.randomStartIntervalMillis(random)));
    }

    private void saveState(Map<EventType, ActiveEvent> events, RandomizationState schedule) throws IOException {
        stateStore.save(new EventSnapshot(events, schedule));
    }

    private EnumMap<EventType, ActiveEvent> mutableSnapshot() {
        var next = new EnumMap<EventType, ActiveEvent>(EventType.class);
        next.putAll(active);
        return next;
    }

    static String multiplierLabel(double multiplier) {
        return BigDecimal.valueOf(multiplier).stripTrailingZeros().toPlainString();
    }

    static String formatTime(long seconds) {
        long hours = seconds / 3600;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    /** Allows the event lifecycle to be tested without a running Minecraft server. */
    interface Display {
        void show(ActiveEvent event, long now);
        void update(ActiveEvent event, long now);
        void hide(EventType type);
        void announce(String message);
        default void startSound() {}
        default void onJoin(ServerPlayer player) {}
        default void onLeave(ServerPlayer player) {}
    }

    private static final class GameDisplay implements Display {
        private final MinecraftServer server;
        private final Map<EventType, ServerBossEvent> bars = new EnumMap<>(EventType.class);
        private final Map<EventType, Long> shownSeconds = new EnumMap<>(EventType.class);

        GameDisplay(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public void show(ActiveEvent event, long now) {
            hide(event.type());
            var color = switch (event.type()) {
                case SHINY -> BossEvent.BossBarColor.YELLOW;
                case ULTRARARE -> BossEvent.BossBarColor.PURPLE;
                case POKEMONXP -> BossEvent.BossBarColor.BLUE;
                case BERRIES -> BossEvent.BossBarColor.GREEN;
            };
            var bar = new ServerBossEvent(Component.literal(event.type().displayName()),
                    color, BossEvent.BossBarOverlay.PROGRESS);
            bars.put(event.type(), bar);
            update(event, now);
            server.getPlayerList().getPlayers().forEach(bar::addPlayer);
        }

        @Override
        public void update(ActiveEvent event, long now) {
            var bar = bars.get(event.type());
            if (bar == null) return;
            bar.setProgress(event.progress(now));
            long seconds = event.remainingSeconds(now);
            if (!Long.valueOf(seconds).equals(shownSeconds.get(event.type()))) {
                shownSeconds.put(event.type(), seconds);
                bar.setName(Component.literal(event.type().displayName() + " | "
                        + multiplierLabel(event.multiplier()) + "x | " + formatTime(seconds) + " remaining"));
            }
        }

        @Override
        public void hide(EventType type) {
            var bar = bars.remove(type);
            if (bar != null) {
                bar.setVisible(false);
                bar.removeAllPlayers();
            }
            shownSeconds.remove(type);
        }

        @Override
        public void announce(String message) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(message)
                    .withStyle(ChatFormatting.GOLD), false);
            ShinyEventsMod.LOGGER.info(message);
        }

        @Override
        public void startSound() {
            EventStartSound.play(server);
        }

        @Override
        public void onJoin(ServerPlayer player) {
            bars.values().forEach(bar -> bar.addPlayer(player));
        }

        @Override
        public void onLeave(ServerPlayer player) {
            bars.values().forEach(bar -> bar.removePlayer(player));
        }
    }
}
