package com.alex.shinyevents.effects;

import com.alex.shinyevents.ShinyEventsMod;
import com.alex.shinyevents.core.EventType;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource;
import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;

/** Server-side hooks for timed events; no Cobblemon configuration is modified. */
public final class EventEffects {
    private EventEffects() {}

    public static void register() {
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.LOWEST, event -> {
            double multiplier = ShinyEventsMod.multiplier(EventType.POKEMONXP);
            if (multiplier > 1 && !event.isCanceled() && event.getSource() instanceof BattleExperienceSource) {
                event.setExperience(EffectMath.boostExperience(
                        event.getExperience(), event.getPokemon().getExperience(), multiplier));
            }
            return Unit.INSTANCE;
        });

        CobblemonEvents.SPAWN_BUCKET_CHOSEN.subscribe(Priority.LOWEST, event -> {
            double multiplier = ShinyEventsMod.multiplier(EventType.ULTRARARE);
            if (multiplier <= 1 || !(event.getSpawner() instanceof PlayerSpawner spawner)) {
                return Unit.INSTANCE;
            }
            var cause = event.getSpawnCause();
            // PlayerSpawner.tick uses this exact cause and its associated player.
            // Fixed-area/snack spawners and specialized fishing causes are excluded.
            if (cause.getClass() != SpawnCause.class
                    || !(cause.getEntity() instanceof ServerPlayer player)
                    || !spawner.getUuid().equals(player.getUUID())) {
                return Unit.INSTANCE;
            }
            var selected = EffectMath.boostBucket(event.getBucketWeights(), event.getBucket(),
                    bucket -> "ultra-rare".equals(bucket.getName()), multiplier,
                    () -> player.serverLevel().getRandom().nextDouble());
            if (selected != event.getBucket()) event.setBucket(selected);
            return Unit.INSTANCE;
        });
    }
}
