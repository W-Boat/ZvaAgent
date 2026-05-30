# Zva / Dia — Android Agent

> DIA. Do it by agent.
> Zva. Company you, I AM HERE.

A minimal Android AI Agent with dual-persona architecture.

## Architecture

```
Zva (companion)  →  Dia (worker)  →  Tools
  ↕                    ↕               ↕
Memory ←→ Persona ←→ API (OpenAI-compatible)
```

**Zva** — warm, emotionally intelligent companion. Handles greetings, emotional support, casual conversation. Delegates tasks to Dia.

**Dia** — precise, efficient task executor. No small talk, just results. Uses tools when needed.

## Features

- **3 tabs**: Chat, History (message-style), Me
- **Tool calling**: calculator, time, reminders, web search, memory
- **Memory system**: accumulates experiences over time, shapes personality
- **OpenAI-compatible API**: configurable endpoint, model, API key
- **Status indicators**: ◀ replying, ◇ thinking, ● working, □ waiting, ●Z calling Dia
- **Customizable persona**: name, model, temperature

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions

Push to `main` or `develop` triggers automatic build. APK artifact available in Actions tab. No signing required.

## Setup

1. Install the APK
2. Go to **Me** tab → **API Settings**
3. Enter your API endpoint (e.g. `https://api.openai.com`), API key, and model
4. Start chatting

## Tools

| Tool | Description |
|------|-------------|
| `get_current_time` | Get current date/time |
| `calculate` | Evaluate math expressions |
| `remember` | Save information to long-term memory |
| `recall_memory` | Search memory |
| `set_reminder` | Set a reminder |
| `web_search` | Web search (stub) |

## Tech Stack

- Kotlin + Jetpack Compose
- Room (memory persistence)
- Hilt (DI)
- Retrofit + OkHttp (API)
- DataStore (preferences)
- Material 3

## License

MIT
