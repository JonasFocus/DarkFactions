<div align="center">

# ⚔️ DarkFactions ⚔️

### A nostalgic factions experience for modern Minecraft servers

**Claim land. Build power. Forge alliances. Raid your rivals.**

<br/>

![Version](https://img.shields.io/badge/version-1.0.0-brightgreen?style=for-the-badge)
![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-blue?style=for-the-badge&logo=minecraft)
![Paper](https://img.shields.io/badge/Paper-API-orange?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-red?style=for-the-badge&logo=openjdk)
![License](https://img.shields.io/badge/license-MIT-lightgrey?style=for-the-badge)

<br/>

[Download 1.0.0](https://github.com/JonasFocus/DarkFactions/releases/tag/v1.0.0) · [Changelog](CHANGELOG.md) · [Config](src/main/resources/config.yml)

</div>

---

<div align="center">

## ✨ Features

</div>

| | |
| :---: | :--- |
| 🏰 **Factions** | Create a faction, invite members, manage roles, and rule together |
| 🌍 **Land Claiming** | Claim chunks with connection rules, buffers, and power-gated limits |
| ⚡ **Power System** | Gain power from kills and raids, lose it on death, decay offline |
| 🧪 **Elixir Economy** | Earn and spend Elixir — the lifeblood of expansion and shop upgrades |
| 🗡️ **Raidable Land** | Low-power factions can be contested; enemies unclaim and earn rewards |
| 🤝 **Alliances** | Request, accept, or deny allies — then coordinate in ally chat |
| 💬 **Faction Chat** | Private channels for your faction and trusted allies |
| ⚙️ **Fully Configurable** | Every limit, cost, and toggle lives in `config.yml` |

---

<div align="center">

## 🚀 Getting Started

</div>

### Requirements

- Paper (or compatible) server on Minecraft **26.1.2**
- **Java 25+**

### Install

1. Download [`DarkFactions-1.0.0.jar`](https://github.com/JonasFocus/DarkFactions/releases/tag/v1.0.0)
2. Drop it into your server's `plugins/` folder
3. Restart the server — config generates at `plugins/DarkFactions/config.yml`
4. Tune values, then run `/f reload`

<details>
<summary><strong>Database</strong></summary>

<br/>

SQLite is the default (file under the plugin data folder). For larger servers, set:

```yaml
database:
  type: MYSQL
```

…and fill in the MySQL connection block.

Data lives in memory and flushes on the auto-save interval (default **300s**) and on shutdown. Setting `general.auto-save-interval-seconds: 0` disables periodic saves — only a clean shutdown will persist changes.

</details>

---

<div align="center">

## 🎮 Commands

</div>

Everything runs through **`/f`**  
*(aliases: `/faction` · `/fac` · `/factions`)*

| Category | Commands |
| :---: | :--- |
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

<div align="center">

### Classic loop

</div>

- **Power gates land** — effective power limits how many chunks you can claim
- **Raidable territory** — with `claim.can-unclaim-enemy: true`, enemies can unclaim low-power land and earn raid rewards
- **Ally requests** — `/f ally <name>` sends a request; the other side accepts or denies
- **Safe disband** — `/f disband` requires typing the faction name when confirmation is enabled

---

<div align="center">

## 🔐 Permissions

</div>

| Permission | Default | Purpose |
| :--- | :---: | :--- |
| `darkfactions.use` | `true` | Use basic faction commands |
| `darkfactions.create` | `true` | Create a faction |
| `darkfactions.ally` | `true` | Manage ally / enemy / neutral relations |
| `darkfactions.admin` | `op` | Admin commands and `/f reload` |

---

<div align="center">

## 🛠️ Build from Source

</div>

```bash
mvn clean package
```

Shaded plugin jar → `target/DarkFactions-1.0.0.jar`

---

<div align="center">

## 📜 License

**MIT** — see [LICENSE](LICENSE)

<br/>

---

<br/>

# ⚔️

### Rally your faction.
### The world is yours to claim.

<br/>

</div>
