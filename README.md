<div align="center">

# ⚔️ DarkFactions

### A nostalgic factions experience for modern Minecraft servers

Claim land. Build power. Forge alliances. Raid your rivals.

<br/>

![Status](https://img.shields.io/badge/status-revived%20%26%20maintained-brightgreen?style=for-the-badge)
![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-blue?style=for-the-badge&logo=minecraft)
![Paper](https://img.shields.io/badge/Paper-API-orange?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-red?style=for-the-badge&logo=openjdk)

</div>

---

> [!NOTE]
> **DarkFactions is back.** This plugin has been revived and is once again actively maintained, rebuilt against the latest Paper API. Expect ongoing fixes, refinements, and new features.

## ✨ Features

- 🏰 **Factions** — create a faction, invite members, manage roles, and rule together.
- 🌍 **Land Claiming** — claim chunks for your faction with connection and buffer rules to keep territory honest.
- ⚡ **Power System** — gain power from kills and raids, lose it on death, and decay while offline. Run out and your land becomes raidable.
- 🧪 **Elixir Economy** — earn and spend Elixir points, the lifeblood of expansion.
- 🤝 **Alliances & Enemies** — set relations, share ally chat, and coordinate against your foes.
- 💬 **Faction & Ally Chat** — private channels for your faction and trusted allies.
- ⚙️ **Fully Configurable** — every limit, cost, and toggle lives in `config.yml`.

## 🚀 Getting Started

**Requirements**

- A Paper (or compatible Spigot) server running Minecraft `26.1.2`
- Java 25+

**Install**

1. Download the latest `DarkFactions-3.1.jar` (or build it yourself, below).
2. Drop it into your server's `plugins/` folder.
3. Restart the server. Configuration generates automatically at `plugins/DarkFactions/config.yml`.

## 🎮 Commands

Everything runs through `/f` (aliases: `/faction`, `/fac`, `/factions`).

| Category | Commands |
| --- | --- |
| **Faction** | `create` · `disband` · `invite` · `accept` · `deny` · `kick` · `leave` · `promote` · `demote` · `leader` · `rename` |
| **Land** | `claim` · `unclaim` · `unclaimall` · `autoclaim` · `map` |
| **Home** | `sethome` · `home` |
| **Info** | `info` · `list` · `show` · `top` |
| **Economy** | `power` · `elixir` · `bal` |
| **Settings** | `motd` · `desc` · `tag` · `open` · `pvp` · `tnt` |
| **Chat** | `chat` · `allychat` |
| **Relations** | `ally` · `enemy` · `neutral` |
| **Admin** | `admin` · `reload` · `fly` |

## 🛠️ Building from Source

```bash
mvn clean package
```

The compiled plugin lands in `target/DarkFactions-3.1.jar`.

## 📜 License

See the repository for license details.

<div align="center">
<br/>

**Rally your faction. The world is yours to claim.**

</div>
