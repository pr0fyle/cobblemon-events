package com.alex.shinyevents;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alex.shinyevents.core.EventType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ShinyEventCommandsTest {
    private static final String PERMISSION_ERROR =
            "You need operator permission level 2 to manage events.";
    private static final String NOT_READY_ERROR =
            "Cobblemon Events is not ready yet. Try again after the server starts.";

    @ParameterizedTest
    @MethodSource("commands")
    void rejectsNonOperatorsEvenWhenPublicCommandNodesAlreadyExist(String command) {
        var managerAccesses = new AtomicInteger();
        var dispatcher = dispatcher(managerAccesses, true);

        var exception = assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute(command, sourceWithPermission(0)));

        assertEquals(PERMISSION_ERROR, exception.getRawMessage().getString());
        assertEquals(0, managerAccesses.get(), "Unauthorized commands must not access event state");
    }

    @ParameterizedTest
    @MethodSource("commands")
    void allowsOperatorsThroughPublicCommandCollisionsToOurHandler(String command) {
        var managerAccesses = new AtomicInteger();
        var dispatcher = dispatcher(managerAccesses, true);

        var exception = assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute(command, sourceWithPermission(2)));

        assertEquals(NOT_READY_ERROR, exception.getRawMessage().getString());
        assertEquals(1, managerAccesses.get(), "The addon handler must replace the existing command");
    }

    @ParameterizedTest
    @MethodSource("commands")
    void ordinaryCommandTreesRejectNonOperatorsBeforeAccessingManager(String command) {
        var managerAccesses = new AtomicInteger();
        var dispatcher = dispatcher(managerAccesses, false);

        assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute(command, sourceWithPermission(0)));

        assertEquals(0, managerAccesses.get());
    }

    @ParameterizedTest
    @MethodSource("commands")
    void ordinaryCommandTreesAllowOperatorsToAccessManager(String command) {
        var managerAccesses = new AtomicInteger();
        var dispatcher = dispatcher(managerAccesses, false);

        var exception = assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute(command, sourceWithPermission(2)));

        assertEquals(NOT_READY_ERROR, exception.getRawMessage().getString());
        assertEquals(1, managerAccesses.get());
    }

    @Test
    void parsesTheEntireEventPoolWithCaseAndWhitespaceNormalization() {
        assertEquals(Set.of(EventType.ULTRARARE, EventType.POKEMONXP, EventType.SHINY),
                ShinyEventCommands.parseEventPool(" ultrarare  POKEMONXP\tshiny "));
        assertEquals(Set.of(EventType.BERRIES), ShinyEventCommands.parseEventPool("berries"));
        assertEquals(EnumSet.allOf(EventType.class),
                ShinyEventCommands.parseEventPool("shiny ultrarare pokemonxp berries"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "shiny shiny", "shiny SHINY", "bogus", "shiny bogus",
            "shiny,berries", "shiny on", "off", "status", "all"})
    void rejectsEmptyUnknownAndDuplicatePoolEntries(String input) {
        assertThrows(IllegalArgumentException.class, () -> ShinyEventCommands.parseEventPool(input));
    }

    @ParameterizedTest
    @MethodSource("invalidPoolCommands")
    void invalidPoolsDoNotAccessOrChangeTheManager(String command) {
        var managerAccesses = new AtomicInteger();
        var dispatcher = dispatcher(managerAccesses, false);

        assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute(command, sourceWithPermission(2)));

        assertEquals(0, managerAccesses.get(), "Validate every pool entry before accessing event state");
    }

    @ParameterizedTest
    @MethodSource("poolSuggestions")
    void suggestsUnusedEventsAndReplacesOnlyTheCurrentWord(String command, Set<String> expected) {
        var managerAccesses = new AtomicInteger();
        var dispatcher = dispatcher(managerAccesses, false);
        var parsed = dispatcher.parse(command, sourceWithPermission(2));

        var suggestions = dispatcher.getCompletionSuggestions(parsed).join().getList().stream()
                .map(suggestion -> suggestion.apply(command)).collect(Collectors.toSet());

        assertEquals(expected, suggestions);
        assertEquals(0, managerAccesses.get());
    }

    private static Stream<String> invalidPoolCommands() {
        return Stream.of("eventrandomize ", "shinyevents randomize ")
                .flatMap(prefix -> Stream.of("bogus", "shiny bogus", "shiny shiny", "shiny SHINY",
                                "ultrarare,shiny", "all", "shiny off")
                        .map(pool -> prefix + pool));
    }

    private static Stream<Arguments> poolSuggestions() {
        return Stream.of(
                Arguments.of("eventrandomize ", Set.of("eventrandomize shiny", "eventrandomize ultrarare",
                        "eventrandomize pokemonxp", "eventrandomize berries", "eventrandomize on",
                        "eventrandomize off", "eventrandomize status")),
                Arguments.of("eventrandomize shiny ", Set.of("eventrandomize shiny ultrarare",
                        "eventrandomize shiny pokemonxp", "eventrandomize shiny berries")),
                Arguments.of("eventrandomize shiny po", Set.of("eventrandomize shiny pokemonxp")),
                Arguments.of("eventrandomize shiny   po", Set.of("eventrandomize shiny   pokemonxp")),
                Arguments.of("eventrandomize shiny UL", Set.of("eventrandomize shiny ultrarare")),
                Arguments.of("shinyevents randomize ultrarare pokemonxp ",
                        Set.of("shinyevents randomize ultrarare pokemonxp shiny",
                                "shinyevents randomize ultrarare pokemonxp berries")),
                Arguments.of("eventrandomize shiny ultrarare pokemonxp berries ", Set.of()),
                Arguments.of("eventrandomize shiny bogus ", Set.of()),
                Arguments.of("eventrandomize shiny shiny ", Set.of()));
    }

    private static java.util.stream.Stream<String> commands() {
        var result = new java.util.ArrayList<String>();
        for (var type : com.alex.shinyevents.core.EventType.values()) {
            result.add("eventstart " + type.id() + " 2x 15m");
            result.add("eventstop " + type.id());
            result.add("eventstatus " + type.id());
            result.add("shinyevents start " + type.id() + " 2x 15m");
            result.add("shinyevents stop " + type.id());
            result.add("shinyevents status " + type.id());
        }
        result.addAll(java.util.List.of("eventstatus", "eventstatus all", "eventstop all",
                "shinyevents status", "shinyevents status all", "shinyevents stop all"));
        for (String root : List.of("eventrandomize", "shinyevents randomize")) {
            result.addAll(List.of(root, root + " on", root + " off", root + " status",
                    root + " ultrarare pokemonxp shiny", root + " berries"));
        }
        return result.stream();
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(
            AtomicInteger managerAccesses, boolean publicCollisions) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        if (publicCollisions) {
            // Brigadier retains earlier requirements when merging matching nodes.
            // Pre-register the complete public paths, including "shiny", so this
            // exercises the execution guard even if child predicates are added.
            dispatcher.register(publicStart("eventstart"));
            dispatcher.register(publicSimple("eventstop"));
            dispatcher.register(publicSimple("eventstatus"));
            dispatcher.register(publicRandomize("eventrandomize"));
            dispatcher.register(literal("shinyevents")
                    .then(publicStart("start"))
                    .then(publicSimple("stop"))
                    .then(publicSimple("status"))
                    .then(publicRandomize("randomize")));
        }
        ShinyEventCommands.register(dispatcher, () -> {
            managerAccesses.incrementAndGet();
            return null;
        });
        return dispatcher;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> publicStart(String root) {
        var builder = literal(root);
        for (var type : com.alex.shinyevents.core.EventType.values()) {
            builder.then(literal(type.id())
                    .then(argument("multiplier", StringArgumentType.word())
                            .then(argument("duration", StringArgumentType.word())
                                    .executes(context -> 99))));
        }
        return builder;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> publicSimple(String root) {
        var builder = literal(root).executes(context -> 99);
        for (var type : com.alex.shinyevents.core.EventType.values()) {
            builder.then(literal(type.id()).executes(context -> 99));
        }
        return builder.then(literal("all").executes(context -> 99));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> publicRandomize(String root) {
        return literal(root).executes(context -> 99)
                .then(literal("on").executes(context -> 99))
                .then(literal("off").executes(context -> 99))
                .then(literal("status").executes(context -> 99))
                .then(argument("events", StringArgumentType.greedyString()).executes(context -> 99));
    }

    private static CommandSourceStack sourceWithPermission(int permissionLevel) {
        // Permission/readiness failures happen before handlers access a world or server.
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO,
                null, permissionLevel, "test", Component.literal("test"), null, null);
    }
}
