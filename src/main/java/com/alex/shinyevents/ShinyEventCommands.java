package com.alex.shinyevents;

import com.alex.shinyevents.core.EventArguments;
import com.alex.shinyevents.core.EventType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

final class ShinyEventCommands {
    private ShinyEventCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                         Supplier<ShinyEventManager> manager) {
        dispatcher.register(start("eventstart", manager));
        dispatcher.register(stop("eventstop", manager));
        dispatcher.register(status("eventstatus", manager));
        dispatcher.register(randomize("eventrandomize", manager));
        dispatcher.register(literal("shinyevents")
                .requires(source -> source.hasPermission(2))
                .then(start("start", manager))
                .then(stop("stop", manager))
                .then(status("status", manager))
                .then(randomize("randomize", manager)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> start(
            String root, Supplier<ShinyEventManager> manager) {
        var builder = literal(root).requires(source -> source.hasPermission(2));
        for (var type : EventType.values()) {
            builder.then(literal(type.id()).requires(source -> source.hasPermission(2))
                    .then(argument("multiplier", StringArgumentType.word())
                            .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(
                                    List.of("2x", "3x", "4x", "8x"), suggestions))
                            .then(argument("duration", StringArgumentType.word())
                                    .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(
                                            List.of("5m", "15m", "30m", "1h", "1h30m"), suggestions))
                                    .executes(context -> executeStart(context, manager, type)))));
        }
        return builder;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stop(
            String root, Supplier<ShinyEventManager> manager) {
        var builder = literal(root).requires(source -> source.hasPermission(2));
        for (var type : EventType.values()) {
            builder.then(literal(type.id()).requires(source -> source.hasPermission(2))
                    .executes(context -> executeStop(context, manager, type)));
        }
        return builder.then(literal("all").requires(source -> source.hasPermission(2))
                .executes(context -> executeStop(context, manager, null)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> status(
            String root, Supplier<ShinyEventManager> manager) {
        var builder = literal(root).requires(source -> source.hasPermission(2))
                .executes(context -> executeStatus(context, manager, null));
        for (var type : EventType.values()) {
            builder.then(literal(type.id()).requires(source -> source.hasPermission(2))
                    .executes(context -> executeStatus(context, manager, type)));
        }
        return builder.then(literal("all").requires(source -> source.hasPermission(2))
                .executes(context -> executeStatus(context, manager, null)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> randomize(
            String root, Supplier<ShinyEventManager> manager) {
        var builder = literal(root).requires(source -> source.hasPermission(2))
                .executes(context -> executeRandomization(context, manager, RandomizationAction.STATUS));
        for (var action : RandomizationAction.values()) {
            builder.then(literal(action.name().toLowerCase(Locale.ROOT))
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> executeRandomization(context, manager, action)));
        }
        return builder.then(argument("events", StringArgumentType.greedyString())
                .suggests((context, suggestions) -> suggestEventPool(suggestions))
                .executes(context -> executeRandomizationPool(context, manager)));
    }

    static Set<EventType> parseEventPool(String input) {
        String choices = Arrays.stream(EventType.values()).map(EventType::id)
                .collect(Collectors.joining(", "));
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Choose at least one event: " + choices + ".");
        }
        var pool = EnumSet.noneOf(EventType.class);
        for (String token : input.trim().split("\\s+")) {
            EventType type;
            try {
                type = EventType.fromId(token);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Unknown event type: " + token + ". Choose from: " + choices + ".");
            }
            if (!pool.add(type)) {
                throw new IllegalArgumentException("Event type is listed more than once: " + type.id() + ".");
            }
        }
        return Collections.unmodifiableSet(pool);
    }

    private static CompletableFuture<Suggestions> suggestEventPool(SuggestionsBuilder suggestions) {
        String remaining = suggestions.getRemaining();
        int wordStart = remaining.length();
        while (wordStart > 0 && !Character.isWhitespace(remaining.charAt(wordStart - 1))) wordStart--;
        var used = EnumSet.noneOf(EventType.class);
        String completed = remaining.substring(0, wordStart).trim();
        if (!completed.isEmpty()) {
            try {
                used.addAll(parseEventPool(completed));
            } catch (IllegalArgumentException invalid) {
                return suggestions.buildFuture();
            }
        }
        var nextWord = suggestions.createOffset(suggestions.getStart() + wordStart);
        return SharedSuggestionProvider.suggest(Arrays.stream(EventType.values())
                .filter(type -> !used.contains(type)).map(EventType::id), nextWord);
    }

    private static int executeRandomizationPool(CommandContext<CommandSourceStack> context,
                                                Supplier<ShinyEventManager> manager)
            throws CommandSyntaxException {
        requirePermission(context.getSource());
        try {
            var pool = parseEventPool(StringArgumentType.getString(context, "events"));
            var current = requireManager(manager);
            current.configureRandomization(pool);
            context.getSource().sendSuccess(() -> Component.literal(current.randomizationStatus()), false);
            return 1;
        } catch (IOException exception) {
            ShinyEventsMod.LOGGER.error("Could not save random event settings", exception);
            throw error("Could not save random event settings; no changes were applied. Check server logs.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw error(exception.getMessage());
        }
    }

    private static int executeRandomization(CommandContext<CommandSourceStack> context,
                                            Supplier<ShinyEventManager> manager, RandomizationAction action)
            throws CommandSyntaxException {
        requirePermission(context.getSource());
        try {
            var current = requireManager(manager);
            if (action == RandomizationAction.ON) current.enableRandomization();
            else if (action == RandomizationAction.OFF) current.disableRandomization();
            String message = action == RandomizationAction.OFF
                    ? "Future random events disabled. Active events continue until their timers end."
                    : current.randomizationStatus();
            context.getSource().sendSuccess(() -> Component.literal(message), false);
            return 1;
        } catch (IOException exception) {
            ShinyEventsMod.LOGGER.error("Could not save random event settings", exception);
            throw error("Could not save random event settings; no changes were applied. Check server logs.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw error(exception.getMessage());
        }
    }

    private enum RandomizationAction {
        ON, OFF, STATUS
    }

    private static int executeStart(CommandContext<CommandSourceStack> context,
                                    Supplier<ShinyEventManager> manager, EventType type)
            throws CommandSyntaxException {
        requirePermission(context.getSource());
        try {
            double multiplier = EventArguments.parseMultiplier(type,
                    StringArgumentType.getString(context, "multiplier"));
            long duration = EventArguments.parseDurationMillis(StringArgumentType.getString(context, "duration"));
            requireManager(manager).start(type, multiplier, duration);
            context.getSource().sendSuccess(() -> Component.literal(type.displayName() + " started."), false);
            return 1;
        } catch (IOException exception) {
            ShinyEventsMod.LOGGER.error("Could not save new event", exception);
            throw error("Could not save event; no new boost was enabled. Check server logs.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw error(exception.getMessage());
        }
    }

    private static int executeStop(CommandContext<CommandSourceStack> context,
                                   Supplier<ShinyEventManager> manager, EventType type)
            throws CommandSyntaxException {
        requirePermission(context.getSource());
        try {
            var current = requireManager(manager);
            if (type == null) current.stopAll();
            else current.stop(type);
            String message = type == null ? "All events stopped." : type.displayName() + " stopped.";
            context.getSource().sendSuccess(() -> Component.literal(message), false);
            return 1;
        } catch (IOException exception) {
            ShinyEventsMod.LOGGER.error("Could not save event cancellation", exception);
            throw error("Could not save cancellation; the event is still running. Check server logs.");
        } catch (IllegalStateException exception) {
            throw error(exception.getMessage());
        }
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context,
                                     Supplier<ShinyEventManager> manager, EventType selected)
            throws CommandSyntaxException {
        requirePermission(context.getSource());
        var current = requireManager(manager);
        current.tick();
        int count = 0;
        for (var type : EventType.values()) {
            if (selected != null && type != selected) continue;
            var event = current.activeEvent(type);
            if (event == null) continue;
            String message = type.displayName() + ": " + ShinyEventManager.multiplierLabel(event.multiplier())
                    + "x, " + ShinyEventManager.formatTime(event.remainingSeconds(
                            System.currentTimeMillis())) + " remaining.";
            context.getSource().sendSuccess(() -> Component.literal(message), false);
            count++;
        }
        if (count == 0) {
            String message = selected == null ? "No events are running."
                    : selected.displayName() + " is not running.";
            context.getSource().sendSuccess(() -> Component.literal(message), false);
        }
        return count;
    }

    private static ShinyEventManager requireManager(Supplier<ShinyEventManager> supplier)
            throws CommandSyntaxException {
        var manager = supplier.get();
        if (manager == null) throw error("Cobblemon Events is not ready yet. Try again after the server starts.");
        return manager;
    }

    private static void requirePermission(CommandSourceStack source) throws CommandSyntaxException {
        // Brigadier preserves existing predicates when another mod shares a node.
        if (!source.hasPermission(2)) {
            throw error("You need operator permission level 2 to manage events.");
        }
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
