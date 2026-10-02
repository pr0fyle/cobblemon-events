package com.alex.shinyevents.audio;

import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Sends the vanilla event-start cue to everyone currently online. */
public final class EventStartSound {
    private EventStartSound() {}

    /** Call once from a successful event start on the server thread. */
    public static void play(MinecraftServer server) {
        // playNotifySound sends a packet directly to this player's connection at
        // their own position, so distance and dimension never exclude recipients.
        // MASTER makes this an event notification and respects master-volume mute.
        for (var player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }
}
