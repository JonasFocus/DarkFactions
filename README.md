# DarkFactions

A nostalgic factions experience for modern Minecraft servers.

Claim land. Build power. Forge alliances. Raid your rivals.

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-blue)
![Paper](https://img.shields.io/badge/Paper-API-orange)
![Java](https://img.shields.io/badge/Java-25-red)
![Version](https://img.shields.io/badge/version-1.0.0-brightgreen)

## Requirements

- Paper (or compatible) server on Minecraft `26.1.2`
- Java 25+

## Install

1. Download `DarkFactions-1.0.0.jar` from [GitHub Releases](https://github.com/JonasFocus/DarkFactions/releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server. Config generates at `plugins/DarkFactions/config.yml`.
4. Tune values, then run `/f reload`.

### Database

SQLite is the default (file under the plugin data folder). For larger servers, set `database.type: MYSQL` and fill in the MySQL connection block.

Data is kept in memory and flushed on the auto-save interval (default 300 seconds) and on shutdown. Setting `general.auto-save-interval-seconds: 0` disables periodic saves; only a clean shutdown will persist changes.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `darkfactions.use` | true | Use basic faction commands |
| `darkfactions.create` | true | Create a faction |
| `darkfactions.ally` | true | Manage ally / enemy / neutral relations |
| `darkfactions.admin` | op | Admin commands and `/f reload` |

## Commands

Everything runs through `/f` (aliases: `/faction`, `/fac`, `/factions`).

| Category | Commands |
| --- | --- |
| **Faction** | `create` · `disband` · `invite` · `uninvite` · `accept` · `deny` · `invites` · `kick` · `leave` · `promote` · `demote` · `leader` · `rename` |
| **Land** | `claim` · `unclaim` · `unclaimall` · `autoclaim` · `map` |
| **Home** | `sethome` · `home` |
| **Info** | `who` / `info` · `list` · `show` · `top [power\|elixir\|members\|land]` |
| **Economy** | `power` · `elixir` · `bal` · `shop` · `transfer` |
| **Settings** | `motd` · `desc` · `tag` · `open` · `pvp` · `tnt` |
| **Chat** | `chat` / `fc` · `allychat` / `ac` |
| **Relations** | `ally` · `ally accept` · `ally deny` · `enemy` · `neutral` |
| **Combat** | `fly` · `logout` |
| **Admin** | `admin` · `reload` |

### Classic loop (1.0)

- Faction effective power limits how many chunks you can claim (`power.power-per-claim`).
- Low-power factions are raidable; with `claim.can-unclaim-enemy: true`, enemies can unclaim raidable land and earn raid rewards.
- `/f ally <name>` sends a request; the other faction accepts or denies.
- `/f disband` requires typing the faction name when confirmation is enabled.

## Building from Source

```bash
mvn clean package
```

The shaded plugin jar is `target/DarkFactions-1.0.0.jar`.

## License

MIT. See [LICENSE](LICENSE).
