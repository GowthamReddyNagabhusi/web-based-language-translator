# 🌐 Web-Based Language Translator

A production-ready, full-stack language translator built with **Java 17** and **vanilla JavaScript**. Translates text across **8 languages** with live translation, pronunciation guides, keyboard shortcuts, shareable results, and persistent exportable history — all from a single deployable JAR.

![Java 17](https://img.shields.io/badge/Java-17-blue) ![Maven](https://img.shields.io/badge/Maven-3.9+-red) ![Jetty](https://img.shields.io/badge/Jetty-11-green) ![Docker](https://img.shields.io/badge/Docker-Ready-blue) ![Version](https://img.shields.io/badge/Version-2.0-purple)

## Features

### Translation Engine
- **Live translation** — real-time as-you-type with configurable debounce
- **8 languages** — French, Spanish, German, Hindi, Japanese, Chinese, Italian, Telugu
- **Cascading API fallback** — Google Translate → LibreTranslate → MyMemory for high availability
- **Pronunciation / romanization** — built-in transliteration for Japanese (Hiragana/Katakana), Hindi, and Telugu scripts
- **Text-to-speech** — listen to translations via Web Speech API

### User Experience
- **Keyboard shortcuts** — `Ctrl+Enter` translate, `Esc` clear, `Ctrl+Shift+C` copy, `Ctrl+Shift+S` share
- **Word count & reading time** — live stats displayed as you type
- **Share translations** — via Web Share API (mobile) or clipboard fallback (desktop)
- **Character limit warning** — visual indicator turns yellow/red near the 5000 char limit
- **Inline progress bar** — animated progress indicator during translation
- **Card glow effects** — subtle focus-within glow on input and translation cards
- **Button micro-animations** — hover shine, scale, and swap rotation effects
- **Smooth page transitions** — fade-in-up animation on card mount (respects `prefers-reduced-motion`)

### History & Data
- **Translation history** — search, filter, and manage past translations (localStorage)
- **Favorite translations** — star/unstar items for quick access
- **Export history** — download all history as a JSON file
- **Import history** — load previously exported history (deduplicates by ID)
- **History stats** — total count, favorites count, filtered count

### Design & Accessibility
- **Gradient navigation bar** — animated gradient header across all pages
- **Dark mode** — system preference detection + manual toggle, persisted across sessions and pages
- **Responsive design** — optimized for mobile, tablet, and desktop
- **Site footer** — consistent footer with version and navigation links
- **Keyboard accessibility** — full focus-visible support and shortcut panel in sidebar

### Production
- **Health endpoint** — `GET /health` for container orchestrators and load balancers
- **Structured logging** — SLF4J + Logback with configurable log levels
- **Docker-ready** — multi-stage Dockerfile with non-root user and health checks

## Tech Stack

| Layer     | Technology                                           |
|-----------|------------------------------------------------------|
| Backend   | Java 17, Embedded Jetty 11, Jakarta Servlets         |
| API       | RESTful JSON (`POST /translate`, `GET /health`)      |
| Frontend  | Vanilla JavaScript (ES Modules), CSS Grid/Flexbox    |
| Sharing   | Web Share API with clipboard fallback                |
| Storage   | localStorage (history, favorites, theme, preferences)|
| Logging   | SLF4J + Logback                                      |
| Build     | Maven with Shade plugin (uber-jar)                   |
| Deploy    | Docker multi-stage build                             |

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+

### Build & Run

```bash
# Build the project
mvn clean package

# Run the server
java -jar target/translator-app-1.0-SNAPSHOT.jar
```

Open **http://localhost:8080** in your browser.

### With Maven (development)

```bash
mvn exec:java -Dexec.mainClass="com.example.translator.WebServer"
```

### With Docker

```bash
docker build -t translator-app .
docker run -p 8080:8080 translator-app
```

## Keyboard Shortcuts

| Shortcut         | Action                     |
|------------------|----------------------------|
| `Ctrl+Enter`     | Translate immediately      |
| `Esc`            | Clear input and output     |
| `Ctrl+Shift+C`   | Copy translation           |
| `Ctrl+Shift+S`   | Share translation           |

## API Reference

### `POST /translate`

Translate text to a target language.

**Request:**
```json
{
  "text": "Hello, how are you?",
  "lang": "fr"
}
```

**Response:**
```json
{
  "translatedText": "Bonjour, comment allez-vous?",
  "pronunciation": "bon-zhoor, koh-mahn tah-lay voo",
  "detectedSourceLang": "auto"
}
```

**Supported language codes:** `fr`, `es`, `de`, `hi`, `ja`, `zh`, `it`, `te`

### `GET /health`

Returns service status for monitoring.

```json
{ "status": "UP", "service": "translator-app" }
```

## Configuration

Edit `src/main/resources/config.properties`:

```properties
server.port=8080
translation.max.length=5000
```

The server also respects the `PORT` environment variable (useful for Docker/Cloud).

## Project Structure

```
src/main/
├── java/com/example/translator/
│   ├── WebServer.java          # Jetty server bootstrap + config
│   ├── TranslateServlet.java   # Translation API endpoint
│   ├── Translator.java         # Translation engine (multi-provider fallback)
│   ├── HealthServlet.java      # Health check endpoint
│   └── Main.java               # CLI translation tool
└── resources/
    ├── config.properties       # Server configuration
    ├── logback.xml             # Logging configuration
    └── webapp/
        ├── index.html          # Main translator UI (shortcuts, progress bar, share)
        ├── history.html        # History page (export/import, favorites, stats)
        ├── about.html          # About page (with theme toggle)
        ├── style.css           # Complete stylesheet (gradient nav, glow, animations, dark mode)
        ├── translator.js       # Core translation logic + keyboard shortcuts + share
        ├── api.js              # HTTP client for /translate API
        ├── utils.js            # Utility functions (debounce, speech, storage, download)
        └── history.js          # History page logic (favorites, export/import, stats)
```

## Architecture Highlights

- **Multi-provider fallback chain** — If Google's unofficial endpoint fails, falls back to LibreTranslate instances, then MyMemory. Ensures high availability.
- **Custom romanization engine** — Hand-crafted character maps for Japanese Hiragana/Katakana, Hindi Devanagari, and Telugu scripts with proper virama/halant handling.
- **Zero-dependency frontend** — No React, no jQuery, no build tools. Pure ES Modules for fast load times and easy deployment.
- **Progressive enhancement** — Web Share API used when available, clipboard fallback otherwise. Animations respect `prefers-reduced-motion`.
- **Production logging** — Structured SLF4J logging replaces all `System.out.println` calls. Configurable per-package log levels via `logback.xml`.
- **Security** — Input validation, character limits, language code whitelist, CORS headers, and non-root Docker user.

## What's New in v2.0

- ✨ Gradient navigation bar with animated styling
- ⌨️ Keyboard shortcuts (Ctrl+Enter, Esc, Ctrl+Shift+C, Ctrl+Shift+S)
- 📊 Live word count and estimated reading time
- 🔗 Share translations via Web Share API
- 📤 Export/Import translation history as JSON
- ⭐ Favorite/star translations in history
- 📊 History statistics (total, favorites, filtered)
- 🎨 Card glow effects on focus
- ✨ Button micro-animations (shine, scale, rotate)
- 📏 Inline progress bar during translation
- ⚠️ Character count warning (yellow at 80%, red at 95%)
- 🌙 Dark mode toggle on ALL pages (including About)
- 🦶 Site-wide footer with navigation
- 🎬 Smooth fade-in-up page transitions
- ♿ Enhanced keyboard accessibility

## License

MIT
