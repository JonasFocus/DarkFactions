# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

DarkFactions is a Bukkit/Paper Minecraft server plugin (a classic "factions" gameplay mod: land claiming, faction power, an Elixir point economy, alliances). Java 17, built with Maven against the Paper API 1.20.4 (`provided` scope). Targets Paper/Spigot servers running `api-version: 1.20`.

## Build & Run

```bash
mvn clean package          # compiles and produces target/DarkFactions-<version>.jar
```

There is no test suite, lint config, or CI. Verification is manual: drop the built jar into a Paper server's `plugins/` folder and run the server (the plugin loads its data and config on `onEnable`).

Note the version mismatch between `pom.xml` (`2.3.1`) and `src/main/resources/plugin.yml` (`1.0.0`); `plugin.yml` is the version the server reports.

## Architecture

The plugin is a manager-based singleton. `DarkFactions` (`onEnable`) is the composition root: it constructs every manager, registers the single command and listener, then loads persisted data. **Construction and data-load order matters** — `FactionManager` must come first because the others reference faction state.

**Managers** (`managers/`) each own one domain, hold all state in-memory (`HashMap`s keyed by `UUID`), and persist to a dedicated YAML file in the plugin data folder on load/`onDisable`:
- `FactionManager` — faction CRUD, membership, invites (`factions.yml`)
- `PowerManager` — per-player/per-faction power; gain on kills/raids, loss on death, offline decay; drives raidability (`playerdata.yml`)
- `ElixirManager` — the Elixir point economy (`elixir.yml`)
- `ClaimManager` — land claims, keyed by `"world:x:z"` chunk strings for O(1) lookup (`claims.yml`)
- `PlayerNameCache` — UUID-to-name cache for offline display (`names.yml`)

Cross-manager access goes through `DarkFactions.getInstance()` getters, not direct references.

**Config is centralized.** All tunable values live in `config.yml` and are read exclusively through `ConfigManager` (`utils/`). Managers do not read config keys directly — they cache values via a `reloadConfig()` method, which `/f reload` re-invokes. When adding a configurable value, add the key to `config.yml`, expose a typed getter on `ConfigManager`, and cache it in the relevant manager's `reloadConfig()`.

**Commands.** A single command `faction` (aliases `/f`, `/fac`, `/factions`) handles everything. `FactionCommand` is a large switch dispatcher routing ~50 subcommands to `handleX` methods; `FactionTabCompleter` mirrors it for completion. Adding a subcommand means touching both, plus `sendHelp`.

**Events.** One listener, `FactionListener`, handles all gameplay enforcement (block break/place protection on claimed land, PvP/TNT rules, explosions, movement-based territory notifications, join/quit, death power changes, and faction/ally chat routing). Chat mode state (normal vs. faction vs. ally chat) lives on `FactionCommand`, which is why the listener holds a reference back to it.

**Models** (`models/`): `Faction` (members, roles, relations, claims, power, settings) and `FactionPlayer` (per-player power/role). User-facing text formatting and color codes are centralized in `MessageUtils`.

## Commit & PR Conventions

- Subject line: industry-standard conventional prefix (`feat:`, `fix:`, `refactor:`, `chore:`), imperative mood, concise.
- Body and PR description: write in first person, present tense, like a developer explaining the change to a teammate. Keep it short and concrete, not verbose. No filler headings.
- Do not use em dashes anywhere.
- Never add a Claude/AI co-author trailer, "Generated with" lines, or any Claude session links to commits or PRs.
- End every PR description with a line noting verification on staging, e.g. "Testing on the staging server to confirm the fix."
