# Changelog

## 1.0.0

First public stable release. Version resets from the internal 3.x line to a clean 1.0.0.

### Gameplay
- Power now gates land claims (`power.power-per-claim`, default 1.0)
- Raidable enemy unclaim when `claim.can-unclaim-enemy` is enabled, with raid power and elixir rewards
- Disband requires typing the faction name when `faction.disband-requires-confirm` is true
- Ally requests require accept/deny instead of instant mutual alliance
- Faction home must be set inside owned claimed land
- Create, rename, and tag elixir costs from config are enforced
- Shop prices and raidable threshold are configurable

### Reliability
- Faction delete scrubs ally/enemy relations on other factions
- Legacy bonus-power migration is crash-safe and idempotent
- Power regen task is cancelled on plugin disable
- Safe logout warmup cancels on movement
- Broader interact protection for doors, trapdoors, fence gates, buttons, and shulker boxes
- Duplicate faction names on load are renamed instead of silently dropped
- Warning when auto-save interval is disabled (shutdown-only persistence)

### Config honesty
- Removed unwired stubs (Vault, metrics, language, update check, quick-start, border particles, session claim limits, playtime elixir)
- Wired admin bypass, admin action logging, chat enable toggles, leaderboard max limit, and power-change messages

### Docs and packaging
- README rewritten for 1.0.0 (install, permissions, database, commands)
- MIT license added
- SqlStore integration tests and claim power-gate unit tests added
