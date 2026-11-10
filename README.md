# OmniCord — Next-Generation Minecraft to Discord Suite

**OmniCord** is a high-speed, feature-rich, and modular Discord bridge and community management plugin designed to replace and outperform **DiscordSRV** and **EssentialsX Discord**.

Built for **Paper**, **Purpur**, **Spigot**, and **Bukkit** servers across versions **1.16 through 1.21+**, OmniCord provides bidirectional chat with webhooks, Discord slash commands, interactive staff moderation buttons, two-way account linking with role and nickname synchronization, a real-time console bridge, live status dashboards, cross-platform trivia minigames, Discord 2FA verification, and in-game support tickets.

---

## Features at a Glance

| Feature | OmniCord Highlights |
| :--- | :--- |
| **Two-Way Chat Bridge** | Webhook integration displaying player skin avatars and names, Discord replies, Discord Markdown to Minecraft formatting, emoji conversion, and mention pings with custom sounds. |
| **Discord 2FA Security Gate** | Freezes OP and staff accounts in-game on login until they click `[Approve Login]` in Discord DMs, protecting server staff from compromised accounts. |
| **Live Pinned Status Dashboard** | Self-updating live embed in `#server-status` with progress bars, player list, TPS/MSPT, memory statistics, and an interactive `[Copy IP]` button. |
| **ModMail & In-Game Tickets** | In-game `/ticket create <issue>` automatically opens a Discord thread in the configured staff channel. Staff replies in Discord are forwarded directly to the player in-game. |
| **Cross-Platform Chat Trivia** | Periodic trivia and math minigames sent to both Minecraft and Discord simultaneously. The first player to answer correctly wins in-game currency and items. |
| **Custom Slash Command Engine** | Define custom Discord slash commands (`/rules`, `/store`, `/vote`) directly inside `custom_commands.yml` with custom embeds without writing code. |
| **Leaderboard Slash Command** | `/leaderboard <balance\|joined>` displays top player rankings on Discord. |
| **Discord Slash Commands** | Includes `/playerlist` with live refresh button, `/lookup <player>`, `/tps` performance diagnostics, `/execute <command>` (console runner), `/link <code>`, and `/rewards`. |
| **Account Linking & Sync** | In-game `/discord link` generates a 6-digit verification code. Synchronizes LuckPerms/Vault groups to Discord roles, syncs Discord nicknames, and runs link reward commands. |
| **Interactive Staff Reports** | In-game `/report <player> <reason>` creates an interactive Discord embed with `[Kick]`, `[Ban]`, and `[Dismiss]` moderation buttons for staff. |
| **Real-Time Console Bridge** | Streams filtered server logs to an administrative Discord channel with ANSI color stripping and sensitive keyword redaction. |
| **Flexible Configuration** | Global prefix with Hex RGB (`&#5865F2`) and MiniMessage support, plus modular YAML files (`config.yml`, `messages.yml`, `embeds.yml`, `rolesync.yml`, `twofactor.yml`, `dashboard.yml`, `tickets.yml`, `trivia.yml`, `custom_commands.yml`). |
| **Database Storage Engine** | Built-in SQLite connection pool (zero-configuration single-server) and MySQL support (for multi-server proxy networks) powered by HikariCP. |

---

## Modular Configuration Files

OmniCord organizes every feature into dedicated configuration files:

1. **`config.yml`**: Master prefix, bot credentials, channel mappings, presence rotation, voice counters, and database settings.
2. **`messages.yml`**: Full in-game and Discord message localization with role colors and hover formatting.
3. **`embeds.yml`**: Embed customizer for Join, Quit, First Join, Death, Advancement, Start/Stop, and Reports.
4. **`rolesync.yml`**: LuckPerms/Vault group-to-role mappings, nickname synchronization, and link rewards.
5. **`twofactor.yml`**: 2FA login verification rules, timeouts, and DM embed styling.
6. **`dashboard.yml`**: Live pinned status dashboard channel, update frequency, and interactive buttons.
7. **`tickets.yml`**: ModMail ticket category, staff role mentions, and ticket dialogues.
8. **`trivia.yml`**: Cross-platform trivia interval, question bank, and reward commands.
9. **`custom_commands.yml`**: User-defined Discord slash commands and custom embed responses.

---

## In-Game Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/omnicord reload` | `omnicord.admin` | Reloads all configuration files and reconnects the Discord bot. |
| `/omnicord status` | `omnicord.admin` | Checks gateway connection status and latency. |
| `/omnicord sync` | `omnicord.admin` | Forces role synchronization for all online linked players. |
| `/discord link` | `omnicord.command.link` | Generates a 6-digit one-time account linking code. |
| `/discord unlink` | `omnicord.command.link` | Unlinks your Discord account. |
| `/discord info` | `omnicord.command.link` | Displays current account link and reward status. |
| `/discord sync` | `omnicord.command.link` | Synchronizes your Discord roles and nickname. |
| `/ticket <create\|reply\|close\|list>` | `omnicord.command.ticket` | Opens or replies to an in-game support ticket. |
| `/staffchat <msg>` (or `/sc`) | `omnicord.staffchat` | Sends a private message to in-game staff and the Discord staff channel. |
| `/report <player> <reason>` | `omnicord.command.report` | Dispatches an interactive report to Discord staff with `[Kick]`, `[Ban]`, and `[Dismiss]` buttons. |

---

## Discord Slash Commands

- `/playerlist` — Interactive embed showing online players, ping, TPS, MSPT, and memory usage, with a **[Refresh]** button.
- `/lookup <player>` — Detailed profile card displaying player skin, UUID, rank, balance, join dates, and link status.
- `/tps` — Performance diagnostic dashboard displaying 1m, 5m, and 15m TPS, MSPT, and JVM memory usage.
- `/leaderboard <type>` — Displays top player leaderboards by Balance or Join Date.
- `/execute <command>` — Administrator console command executor with confirmation.
- `/link <code>` — Links a Discord account using the in-game 6-digit verification code.
- `/rewards` — Checks link reward status.
- **Custom commands** (`/rules`, `/store`, `/vote`, etc.) defined in `custom_commands.yml`.

---

## Installation & Building from Source

1. Download `OmniCord-0.0.1-alpha.jar` from the [Releases](https://github.com/PantherYTac/OmniCord/releases) page.
2. Place the `.jar` file inside your server's `/plugins` directory and restart the server.
3. Configure your Discord bot token and channels in `plugins/OmniCord/config.yml` and run `/omnicord reload`.

### Build from Source (Java 17+)

```bash
git clone https://github.com/PantherYTac/OmniCord.git
cd OmniCord
./gradlew shadowJar
```

The compiled plugin JAR will be generated at `build/libs/OmniCord-0.0.1-alpha.jar`.

---

## License

**All Rights Reserved.** Copyright © 2026 **PantherYTac**.

This software is proprietary. No permission is granted to copy, distribute, modify, reverse engineer, decompile, re-upload, or create derivative works of this plugin in source or binary form without the prior express written permission of the author (**PantherYTac**).
