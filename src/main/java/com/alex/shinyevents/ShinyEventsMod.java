package com.alex.shinyevents;

import com.alex.shinyevents.core.ActiveEvent;
import com.alex.shinyevents.core.EventType;
import com.alex.shinyevents.effects.EventEffects;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import kotlin.Unit;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShinyEventsMod implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("shiny_events");
    private static volatile ShinyEventManager manager;

    public static double multiplier(EventType type) {
        var current = manager;
        return current == null ? 1 : current.multiplier(type);
    }

    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ShinyEventCommands.register(dispatcher, () -> manager));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            manager = new ShinyEventManager(server);
            manager.restore();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var current = manager;
            if (current != null) current.tick();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var current = manager;
            if (current != null) current.onJoin(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var current = manager;
            if (current != null) current.onLeave(handler.getPlayer());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var current = manager;
            manager = null;
            if (current != null) current.close();
        });

        // Runs after ordinary modifiers, and never edits Cobblemon's configuration.
        CobblemonEvents.SHINY_CHANCE_CALCULATION.subscribe(Priority.LOWEST, event -> {
            var current = manager;
            if (current != null && current.activeEvent(EventType.SHINY) != null) {
                event.addModificationFunction((rate, player, pokemon) -> {
                    double multiplier = current.multiplier(EventType.SHINY);
                    return multiplier > 1 ? ActiveEvent.boostShinyRate(rate, multiplier) : rate;
                });
            }
            return Unit.INSTANCE;
        });
        EventEffects.register();
        LOGGER.info("Cobblemon Events loaded: shiny, ultrarare, pokemonxp, berries.");
    }
}
