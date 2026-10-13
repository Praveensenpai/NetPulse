---
name: kuroko
description: >-
  Manages, configures, and reconciles Telegram bots declaratively via the Kuroko (黒子) CLI.
  Use whenever inspecting bot fleets, updating bot command menus, editing bot bios/descriptions,
  syncing avatars/video covers, or creating new bots via BotFather.
---

# `kuroko` (黒子) Telegram Bot Fleet Management Skill

Automates the configuration, command synchronization, and lifecycle management of Telegram bots through the `kuroko` CLI. Enables AI agents to manage bot fleets declaratively with zero manual TOML editing.

---

## 1. When to Use
- **Inspecting Bot Fleet State**: Querying registered bots, live usernames, command lists, and synchronization status (`kuroko list`, `kuroko cmd list <bot>`).
- **Updating Bot Commands**: Adding, modifying, or removing `/command` descriptions when implementing new bot features in code (`kuroko cmd add <bot> <cmd> "<desc>"`).
- **Updating Bot Metadata**: Updating bot display names, about text, short bios, or avatar media (`kuroko bot set <bot> ...`).
- **Reconciling Desired vs Live State**: Computing diffs against Telegram servers and applying changes (`kuroko diff`, `kuroko apply --http-only`).
- **Auto-Discovering Host Bots**: Automatically scanning host configuration files (`~/.config/ryoiki/telegram.json`, `~/.config/tayori/config.toml`) and pulling live state (`kuroko import --auto`).
- **Provisioning New Bots**: Automating `/newbot` creation dialogues with `@BotFather` over MTProto (`kuroko new`).

---

## 2. Agent Usage & Command Patterns

### A. Zero-Manual Configuration (Mutating `bots.toml`)
> [!IMPORTANT]
> **Never manually edit `bots.toml` files.** Always use the dedicated CLI subcommands. Kuroko automatically resolves and updates `~/.config/kuroko/bots.toml` by default.

```bash
# 1. Add or update a command for a bot
kuroko cmd add <bot_id> <command_name> "<description>"

# Example:
kuroko cmd add ryoiki status "Show system health, CPU, RAM, & active downloads"
kuroko cmd add ryoiki torrents "List active and completed torrents"

# 2. List currently configured commands for a bot
kuroko cmd list <bot_id>

# 3. Remove an obsolete command
kuroko cmd rm <bot_id> <command_name>

# 4. Set or update bot metadata
kuroko bot set <bot_id> \
  --name "<Display Name>" \
  --token "env:<TOKEN_ENV_VAR>" \
  --desc "<Full long description>" \
  --short-desc "<Short bio (max 120 chars)>"
```

### B. Auto-Importing Existing Host Bots
When first running on a new machine or detecting an unmanaged bot on the system:
```bash
# Auto-detects local bot tokens from known directories and pulls live state
kuroko import --auto

# Or import an individual bot by token
kuroko import --token "123456:ABC-DEF..." --id <bot_id>
```

### C. State Diffing & Deployment
```bash
# 1. Preview changes (diff local desired state vs live Telegram state)
kuroko diff

# 2. Apply changes immediately via Telegram Bot API (commands, descriptions, names)
kuroko apply --http-only

# 3. Full reconcile (including MTProto BotFather avatars/covers/privacy settings)
kuroko apply
```

### D. Fleet Overview Dashboard
```bash
kuroko list
```

---

## 3. Operational Rules for AI Agents

1. **Zero Manual Editing**: Never modify `bots.toml` using text replacement or manual file editing. Use `kuroko cmd add`, `kuroko cmd rm`, `kuroko bot set`, or `kuroko import`.
2. **Safe Secrets (Zero Token Leaks)**: Always store bot tokens as environment references (e.g. `env:RYOIKI_BOT_TOKEN`, `env:TAYORI_BOT_TOKEN`) rather than hardcoding plaintext secrets in committed files.
3. **Verify Diffs Before Applying**: Always run `kuroko diff` to inspect planned changes before executing `kuroko apply`.
4. **Use `--http-only` for Headless Sync**: When updating commands, descriptions, or names without interactive user terminal presence, always pass `--http-only` to avoid blocking on MTProto 2FA login prompts.
5. **Keep Commands Synchronized With Code**: Whenever adding a new bot command handler in application source code (e.g. `router.rs`), immediately register the command in Kuroko:
   ```bash
   kuroko cmd add <bot_id> <new_command> "<Clear explanation>"
   kuroko apply --http-only
   ```
