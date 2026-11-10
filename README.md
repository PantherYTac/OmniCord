# 🌟 OmniCord — Next-Generation Minecraft <-> Discord Suite

**OmniCord** is a high-speed, feature-rich, and modular Discord bridge and community management plugin designed to replace and significantly outperform **DiscordSRV** and **EssentialsX Discord**.

Built for **Paper**, **Purpur**, **Spigot**, and **Bukkit** servers across versions **1.16 through 1.21+ / modern releases**, OmniCord provides bidirectional chat with Webhooks, Discord Slash Commands, interactive staff moderation buttons, 2-way account linking with role & nickname synchronization, real-time console bridge, live status dashboards, cross-platform trivia minigames, Discord 2FA verification, and in-game support tickets!

---

## ✨ Features at a Glance

| Feature | OmniCord Highlights |
| :--- | :--- |
| 💬 **Two-Way Chat Bridge** | Webhook integration displaying exact player skin avatars & names, Discord replies, Discord Markdown to Minecraft formatting, emoji conversion, mention pings with custom sounds. |
| 🛡️ **Discord 2FA Security Gate** | Freezes OP/Staff in-game on login until they click `[✅ Approve Login]` in Discord DMs! Protects server staff from compromised accounts. |
| 📊 **Live Pinned Status Dashboard** | Self-updating live embed in `#server-status` with Unicode progress bars (`[████████░░]`), player list, TPS/MSPT, memory stats, and interactive `[📋 Copy IP]` button. |
| 🎫 **ModMail & In-Game Tickets** | In-game `/ticket create <issue>` automatically opens a Discord thread in your staff channel. Staff reply in Discord, and replies are forwarded to the player in-game! |
| 🧠 **Cross-Platform Chat Trivia** | Periodic trivia & math minigames sent to both Minecraft and Discord simultaneously! First player to answer correctly wins in-game money & items! |
| 🛠️ **Custom Slash Command Engine** | Define custom Discord slash commands (`/rules`, `/store`, `/vote`) directly inside `custom_commands.yml` with custom embeds—zero code needed! |
| 🏆 **Leaderboard Slash Command** | `/leaderboard <balance\|joined>` displays top player rankings with medals (`🥇`, `🥈`, `🥉`) on Discord. |
| ⚡ **Discord Slash Commands** | Modern `/playerlist` with live refresh button, `/lookup <player>`, `/tps` performance gauge, `/execute <command>` (Console runner), `/link <code>`, `/rewards`. |
| 🔗 **Account Linking & Sync** | In-game `/discord link` generates a 6-digit code. Syncs LuckPerms/Vault groups to Discord roles, synchronizes Discord nicknames, and runs link reward commands. |
| 🚨 **Interactive Staff Reports** | In-game `/report <player> <reason>` creates an interactive Discord embed with `[👢 Kick]`, `[🔨 Ban]`, and `[✖ Dismiss]` action buttons for staff. |
| 💻 **Real-Time Console Bridge** | Streams filtered server logs to an admin Discord channel with ANSI color stripping and sensitive keyword redaction (hides passwords/tokens). |
| 🎨 **Ultra-Easy Configuration** | Global prefix with Hex RGB (`&#5865F2`) and MiniMessage support, modular YAML files (`config.yml`, `messages.yml`, `embeds.yml`, `rolesync.yml`, `twofactor.yml`, `dashboard.yml`, `tickets.yml`, `trivia.yml`, `custom_commands.yml`). |
| 💾 **Robust Database Storage** | Built-in SQLite connection pool (zero-configuration single-server) and MySQL support (for multi-server proxy networks) powered by HikariCP. |

---

## 📁 Modular Configuration Files

OmniCord organizes every feature into clear, dedicated files:

1. **`config.yml`**: Master prefix, bot credentials, channel mappings, presence rotation, voice counters, database.
2. **`messages.yml`**: Full in-game and Discord message localization with role colors and hover formatting.
3. **`embeds.yml`**: Embed customizer for Join, Quit, First Join, Death, Advancement, Start/Stop, and Reports.
4. **`rolesync.yml`**: LuckPerms/Vault group-to-role mappings, nickname synchronization, and link rewards.
5. **`twofactor.yml`**: 2FA login verification rules, timeouts, and DM embed styling.
6. **`dashboard.yml`**: Live pinned status dashboard channel, update frequency, and buttons.
7. **`tickets.yml`**: ModMail ticket category, staff role mentions, and ticket dialogues.
8. **`trivia.yml`**: Cross-platform trivia interval, question bank, and prize commands.
9. **`custom_commands.yml`**: User-defined Discord slash commands and custom embed responses.

---

## ⌨️ In-Game Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/omnicord reload` | `omnicord.admin` | Reloads all configuration files and reconnects Discord bot. |
| `/omnicord status` | `omnicord.admin` | Checks gateway status and ping. |
| `/omnicord sync` | `omnicord.admin` | Forces role synchronization for all online linked players. |
| `/discord link` | `omnicord.command.link` | Generates a 6-digit one-time linking code. |
| `/discord unlink` | `omnicord.command.link` | Unlinks Discord account. |
| `/discord info` | `omnicord.command.link` | Displays current account link status and reward status. |
| `/discord sync` | `omnicord.command.link` | Self-synchronizes Discord roles and nickname. |
| `/ticket <create\|reply\|close\|list>` | `omnicord.command.ticket` | Opens or replies to an in-game support ticket. |
| `/staffchat <msg>` (or `/sc`) | `omnicord.staffchat` | Sends private message to in-game staff and Discord staff channel. |
| `/report <player> <reason>` | `omnicord.command.report` | Dispatches an interactive report to Discord staff with `[Kick]`, `[Ban]`, and `[Dismiss]` buttons. |

---

## 🤖 Discord Slash Commands

- `/playerlist` — Interactive embed showing online players, ping, TPS, MSPT, memory, with a **[🔄 Refresh]** button.
- `/lookup <player>` — Detailed profile card with player skull, UUID, rank, balance, join dates, and link status.
- `/tps` — Diagnostic dashboard with colored gauges (🟢 🟡 🔴) for 1m, 5m, 15m TPS, MSPT, and JVM memory.
- `/leaderboard <type>` — Displays top player leaderboards (Balance or Join Date) with medals (`🥇`, `🥈`, `🥉`).
- `/execute <command>` — Admin console executor with responsive confirmation.
- `/link <code>` — Connects Discord account using in-game 6-digit PIN.
- `/rewards` — Checks link reward status.
- **Custom commands** (`/rules`, `/store`, `/vote`, etc.) defined in `custom_commands.yml`.

---

## 📦 Installation & Building from Source

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

## 📄 License

**All Rights Reserved.** Copyright © 2026 **PantherYTac**.

This software is proprietary. No permission is granted to copy, distribute, modify, reverse engineer, decompile, re-upload, or create derivative works of this plugin in source or binary form without the prior express written permission of the author (**PantherYTac**).

