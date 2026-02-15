# AskAI

A PaperMC plugin that lets players chat with AI providers in-game. Supports OpenAI, Anthropic, and Google Gemini.

On 'player' mode, each player picks a provider and model, sends messages with `/chat`, and gets private AI responses. On 'server' mode, the server sets a single API key and model for all players.

## Requirements

- Paper or Purpur 1.20.6+

## Installation

1. Download the latest JAR from [Releases](https://github.com/rohan-dogra/AskAI/releases)
2. Drop it into your server's `plugins/` folder
3. Restart the server
4. Edit `plugins/AskAI/config.yml` — at minimum, change the `encryption.seed` to a random string

## Commands

| Command | Description |
|---------|-------------|
| `/chat <message>` | Send a message to your active AI provider |
| `/chat setkey <provider> <key>` | Set your API key for a provider |
| `/chat setmodel <provider> <model>` | Set which model to use for a provider |
| `/chat provider <provider>` | Switch your active provider |
| `/chat status` | Show your current config and key status |

Providers: `openai`, `anthropic`, `gemini`

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `askai.use` | All players | Use `/chat` and `/chat status` |
| `askai.setkey` | All players | Manage keys, models, and provider selection |
| `askai.admin` | OP only | Set server-wide API keys in server-key mode |

## Key Modes

### Player mode (default)

Each player sets and manages their own API keys. This is the default behavior.

### Server mode

One set of API keys is shared by all players, managed by admins. Players can still choose their own provider and model, but the API key comes from the server.

To enable, set `key-mode: "server"` in `config.yml` and restart. Only players with `askai.admin` permission can set keys in this mode.

## Configuration

```yaml
# Encryption seed for API key storage. Set this once before first use.
# Changing it after players have set keys makes their keys unrecoverable.
encryption:
  seed: "CHANGE-ME-use-a-long-random-string-here"

# "player" (each player sets own keys) or "server" (admin sets shared keys)
key-mode: "player"

# Rate limiting per player
rate-limit:
  requests: 10
  window-seconds: 60

# Message limits
max-message-length: 2000
max-response-tokens: 1024

# System prompt prepended to all conversations
system-prompt: "You are a helpful assistant in a Minecraft server."

# Which providers are enabled
allowed-providers:
  - openai
  - anthropic
  - gemini
```
