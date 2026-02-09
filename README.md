# Translator App

A small Java-based translation demo using an embedded Jetty server and several public translation endpoints. The project demonstrates HTTP client usage, JSON handling, simple transliteration helpers, a minimal web UI, and file-backed history persistence. It is intended as an interview-ready project showcasing design, logging, configuration, and testable components.

## Features
- Web UI for entering text and selecting a target language
- HTTP API endpoint `/translate` (accepts JSON or form data)
- Multiple translation providers (Google unofficial endpoint, LibreTranslate, MyMemory) with fallbacks
- Pronunciation / transliteration helpers for Japanese, Hindi, Telugu
- File-backed translation history (`translation-history.json`)
- Configurable server port via `src/main/resources/config.properties`
- Structured logging with SLF4J + Logback

## Tech stack
- Java 17
- Maven
- Embedded Jetty
- Gson for JSON
- SLF4J + Logback for logging

## Quick start

Build the project (requires Maven):

```bash
mvn clean package
```

Run the server (from project root):

```bash
java -jar target/translator-app-1.0-SNAPSHOT.jar
# or with maven
mvn exec:java -Dexec.mainClass="com.example.translator.WebServer"
```

Open http://localhost:8080 in a browser and use the UI to translate text.

## Configuration
Edit `src/main/resources/config.properties` to change `server.port` or `history.file`.

## Development notes
- The translation logic is in `Translator.java`. It tries multiple providers and attempts to extract a readable pronunciation where available.
- The servlet is `TranslateServlet.java`. It returns JSON when requested and writes concise error messages otherwise.
- `HistoryManager` records each successful translation to a JSON file in the working directory.

## What I improved
- Replaced print statements with SLF4J logging, added structured logging.
- Added configuration support and a small history persistence layer.
- Improved error handling and validation in the servlet.
- Added a professional README and inline comments for maintainability.

## Next steps / Ideas
- Add unit tests for transliteration helpers.
- Add authentication / rate-limiting for public deployments.
- Persist history to a small embedded DB (H2) for richer queries.
