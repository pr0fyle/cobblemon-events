# Cobblemon Events

A **server-only Fabric addon** for Minecraft **1.21.1** and Cobblemon **1.7.3**. Adds independently timed shiny, ultra-rare spawn, Pokémon battle XP, and berry-growth events, plus an optional random-event scheduler. It was developed for the versions used by Cobbleverse **1.7.42**; the modpack itself is not required.

## Install or update

1. Stop the Minecraft server.
2. Remove any previous `shiny-events-*.jar` addon from the server's `mods` folder.
3. Download **`shiny-events-1.2.0.jar`** from [GitHub Releases](https://github.com/pr0fyle/cobblemon-events/releases/latest), or build it using the instructions below, and copy it into the server's `mods` folder. Do not install the `-sources.jar`, the source-code ZIP, or multiple addon versions.
4. Start the server and run the commands below as an operator.

**Your friends do not install this addon.** Boss bars, XP, spawns, and berry updates use the existing Minecraft/Cobblemon client behavior. The addon loads on dedicated servers only, not client-hosted singleplayer or LAN worlds.

Requires Fabric API (build target: 0.116.6+1.21.1), Fabric Loader 0.17.2 or newer, Cobblemon 1.7.3, and Java 21 or newer. Minecraft must be 1.21.1. Cobbleverse already supplies these dependencies. The mod's internal ID remains `shiny_events` so this replaces the earlier release.

## Commands

All commands require Minecraft operator permission level **2** or higher. The server console can run them without the leading `/`.

| Command | Effect | Boss bar |
| --- | --- | --- |
| `/eventstart shiny 8x 15m` | 8x shiny odds for new eligible rolls | Gold |
| `/eventstart ultrarare 2x 30m` | 2x ultra-rare category selection probability | Purple |
| `/eventstart pokemonxp 2x 1h` | 2x Pokémon battle XP | Blue |
| `/eventstart berries 3x 1h` | 3x Cobblemon berry growth speed | Green |
| `/eventstatus` | List all active events and remaining times | |
| `/eventstatus pokemonxp` | Inspect one event | |
| `/eventstop pokemonxp` | Stop only the battle XP event | |
| `/eventstop all` | Stop every active event | |

Manually started events of different types can run together. Each has its own multiplier, deadline, and shrinking countdown bar. Players joining during events see all active bars. Starting a second event of the same type is rejected; stop that type before restarting it. Stopping or expiring one type leaves the other events intact.

The original `/shinyevents` command root still works if another addon conflicts with the short commands:

```mcfunction
/shinyevents start pokemonxp 2x 1h
/shinyevents status
/shinyevents status shiny
/shinyevents stop berries
/shinyevents stop all
```

Durations accept whole-number `s`, `m`, `h`, and `d` components, including `30s`, `15m`, `1h30m`, and `1d`. Durations must be between 1 second and 7 days. Multipliers can omit `x`, support decimals such as `2.5x`, and must be greater than 1. Berry growth is capped at **64x** to bound work per server tick; other types accept up to 1,000,000x, with chance/XP calculations capped safely.

## Random events

Enable automatic events using exactly the types you want:

```mcfunction
/eventrandomize ultrarare pokemonxp shiny
```

This example randomly chooses **one** of those three types each time. Berries are excluded. To include all four:

```mcfunction
/eventrandomize shiny ultrarare pokemonxp berries
```

| Command | Result |
| --- | --- |
| `/eventrandomize <types...>` | Enable randomness with the specified selection, replacing the saved selection |
| `/eventrandomize off` | Disable future random events; currently active events continue |
| `/eventrandomize on` | Enable the saved selection, or all four types if none was saved |
| `/eventrandomize status` | Show whether it is enabled, selected types, and time until the next start |
| `/eventrandomize` | Same as status |

`/shinyevents randomize ...` is an equivalent command. All randomization commands require operator level 2. Tab completion suggests event names and excludes ones already selected. Unknown names and duplicates produce an error without changing the selection.

Every automatic event uses these inclusive ranges:

| Event | Random whole-number multiplier |
| --- | --- |
| `shiny` | 4x–32x |
| `ultrarare` | 2x–4x |
| `pokemonxp` | 3x–10x |
| `berries` | 5x–20x |

- **Duration:** a random whole number of minutes from **5 to 20**.
- **Spacing:** a random whole number of minutes from **60 to 150 between event START times**, not after an event ends.
- **Selection:** each enabled type has an equal chance; the same type can be picked consecutively.
- **First start:** normally the next server tick after enabling. A cooldown from a recent event or a currently active event is honored.
- **Pool changes/off/on:** preserve the existing next-start deadline; they do not reset the cooldown or replace an active event.
- **Restart:** the enabled setting, pool, and absolute next-start time are saved. An overdue schedule starts one new event when the server resumes and is clear of active events; missed events are not replayed in a burst.

Automatic events only start when no other event is active. Manual `/eventstart` commands remain available and can overlap an existing manual or automatic event. A manual start schedules the next automatic start 60–150 minutes from that manual start, and automatic events wait if a long manual event is still running. Manual overrides, downtime, lag, or storage failures can therefore delay an automatic start beyond its scheduled time. While the server is running, the random schedule also runs when no players are online.

`/eventstop all` stops current events but leaves randomization enabled. To turn off automation and end the current events, use `/eventrandomize off` and then `/eventstop all`.

## Start sound

Every successfully started event, **manual or random**, plays Minecraft's **Wither spawn sound** once to each online player, regardless of distance or dimension. It uses the existing client sound and honors the player's master volume. Restoring an already-running event after a restart or joining one in progress does not replay the start sound.

## What each event affects

### Pokémon battle XP

Only rewards marked by Cobblemon as **battle experience** receive the multiplier. XP candies, XP commands, unrelated addon rewards, and Minecraft player/enchanting XP are unaffected. Battle rewards distributed through Cobblemon's usual battle XP pipeline, including shared XP, retain that pipeline's behavior.

The multiplier is applied to the incoming battle award, allowing ordinary prior bonuses to contribute. Fractional results round to the nearest whole XP point; overflow is prevented and Cobblemon's normal level handling remains in place. Other addons that subsequently overwrite the award can still change the result.

### Ultra-rare spawns

This boosts the **`ultra-rare` rarity category** in the normal player spawning system, not the total number of spawn attempts. The addon uses the final category weights after Cobblemon's normal influences and increases ultra-rare selection probability by the specified factor, capped at 100%. Other categories retain their relative proportions.

For example, an existing 0.1% ultra-rare category probability becomes 0.2% during a 2x event. The calculation multiplies the probability, not just the raw weight. Actual encounter counts still depend on randomness, spawn caps, and available Pokémon satisfying biome, time, weather, and other conditions. Ultra-rare is a spawn category, not a guarantee of legendary Pokémon.

This covers normal spawning around players and `/spawnpokemonfrompool`, which uses the same player spawner. Fishing, Poké Snacks, fixed-area/custom spawners, and explicit `/spawnpokemon` creation are excluded. An absent or zero-weight ultra-rare category is not enabled. Other addons replacing category selections may affect the combined odds.

### Berry growth

Only **Cobblemon berry plants in ticking chunks** grow faster. Their existing growth logic is run more often, preserving growth stages, mulch, mutations, and yields. Rooted plants remain rooted; fully fruiting plants receive no extra growth ticks. Existing progress remains after the event ends, and subsequent growth returns to normal speed.

It does not change Minecraft's global random tick speed, vanilla crops, sweet berry bushes, apricorns, or unloaded areas. Fractional multipliers use randomized extra ticks for the requested average speed. The growth rate is measured per server tick, so low server TPS still slows growth in real time.

### Shiny odds

The original shiny feature uses Cobblemon's `SHINY_CHANCE_CALCULATION` hook. It changes new rolls only; existing Pokémon are not rerolled. If normal odds are 1 in 8,192, an 8x event changes that roll to 1 in 1,024. Probabilities are capped at 100%.

Natural spawns and initial fishing rolls use this hook. Fishing bait's separate bonus reroll is unchanged. Explicit fixed shiny properties bypass the random result. Breeding/reward addons are affected only when they use the same chance hook and do not overwrite its result.

## Timers, restarts, and persistence

Events are saved atomically in **`<world>/data/shiny-events.json`**. Existing version 1 shiny saves and version 2 multi-event saves are automatically migrated with their original deadlines preserved; randomization starts disabled for older saves. Version 3 stores active events and the random schedule together in one atomic update.

- Different event types have independent deadlines.
- Real time continues passing while the server is offline, empty, or lagging.
- Restarting resumes unexpired events and discards expired ones.
- Stopping an event removes only that event from storage.
- No Cobblemon configuration is permanently changed.
- Invalid/corrupt saves are logged and no boosts are restored from them.
- A failed save prevents a new event from starting. A failed cancellation keeps the event running so it can be retried.

Timers use the server's system clock; manually changing that clock affects deadlines. The boss bar updates when the server ticks.

## Build and validation

Use a **Java 21 JDK**, set `JAVA_HOME` to it, and run:

```powershell
.\gradlew.bat build
```

On Linux/macOS: `sh ./gradlew build`.

The Gradle wrapper verifies its pinned distribution's SHA-256. Dependencies target the exact official Cobblemon 1.7.3 Fabric artifact. Cobblemon and Fabric API are not bundled into the addon; Cobblemon provides the Kotlin runtime.

The output is **`build/libs/shiny-events-1.2.0.jar`**.

The build passes **359 automated tests** covering command authorization (including shared-name conflicts), independent events, restart recovery and legacy migration, malformed saves, shiny math, exact rarity probability multiplication, XP rounding/overflow, berry tick budgets, input bounds, randomized ranges, start-to-start scheduling, enable/disable persistence, recovery after failed saves, and sound triggers. A separate production Fabric Loader 0.17.2 smoke test loaded the release JAR with Cobblemon 1.7.3 and confirmed that the berry mixin successfully transformed its target class. That test exited before Minecraft world startup.

Full Cobbleverse integration and Minecraft client visuals have not been playtested here. After installation:

1. Start shiny and battle XP events with different short durations; confirm separate bars and expiry.
2. Stop one event and confirm the other remains. Join as another player and check the bars.
3. Compare battle XP with and without the XP event; confirm candy XP does not receive the event bonus.
4. Start a berry event near growing Cobblemon berry plants and confirm accelerated growth, normal rooted behavior, and normal speed after expiry.
5. Test ultra-rare selection in an area with valid ultra-rare spawns. Short runs cannot establish statistical odds; automated tests verify the probability calculation.
6. Restart with multiple events active and confirm the deadlines were preserved and offline time was deducted.
7. Enable a selected random pool, confirm the first event is from that pool, and check `/eventrandomize status` for the next start. Disable it and confirm the current event continues while future starts stop.
8. Have players in different dimensions confirm they hear the Wither spawn cue on a new event.

## Contributing and license

[Bug reports](https://github.com/pr0fyle/cobblemon-events/issues) and pull requests are welcome. Include the addon, Minecraft, Fabric Loader, Fabric API, Cobblemon, and modpack versions when reporting a problem, along with reproduction steps and relevant server logs. Remove private information from logs before posting them.

For code changes, use Java 21 and run `sh ./gradlew build` (or `.\gradlew.bat build` on Windows). GitHub Actions also builds the addon and runs its tests for pushes and pull requests. Changes to Cobblemon hooks should be checked against the exact supported Cobblemon version and tested on a dedicated server.

The addon is available under the [MIT license](LICENSE). The Gradle wrapper retains its upstream Apache 2.0 license notices. Minecraft, Cobblemon, and other dependencies are separate projects under their own terms and are not bundled in this addon. This is an unofficial community addon.

## Sources

- [Exact Cobblemon 1.7.3 Fabric release](https://modrinth.com/mod/cobblemon/version/kF7CvxTo)
- [Cobblemon XP event](https://gitlab.com/cable-mc/cobblemon/-/blob/1.7.3/common/src/main/kotlin/com/cobblemon/mod/common/api/events/pokemon/ExperienceGainedEvent.kt)
- [Cobblemon spawn-category selection](https://gitlab.com/cable-mc/cobblemon/-/blob/1.7.3/common/src/main/kotlin/com/cobblemon/mod/common/api/spawning/spawner/Spawner.kt)
- [Cobblemon berry growth](https://gitlab.com/cable-mc/cobblemon/-/blob/1.7.3/common/src/main/kotlin/com/cobblemon/mod/common/block/entity/BerryBlockEntity.kt)
- [Cobblemon shiny chance calculation](https://gitlab.com/cable-mc/cobblemon/-/blob/1.7.3/common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/PokemonProperties.kt)
