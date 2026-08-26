# ReadStack

> A knowledge-first Android app: **search, download, index, and read** official documentation and books from across the web.

ReadStack is a fully working Compose Android client for four kinds of sources,
all in one place, with offline reading, full-text search across your library,
and lightweight highlights / notes — built with **Material 3**, **Room**,
**WorkManager**, and **Hilt**.

```
┌─────────────────── ReadStack ───────────────────┐
│  Library   Browse   Search   Settings           │
│ ┌──────────┐ ┌────────────────────────────────┐ │
│ │ ▢ ▢ ▢ ▢ │ │ Trending on GitHub              │ │
│ │ ▢ ▢ ▢ ▢ │ │  ┌─────────┐ ┌─────────┐       │ │
│ │ ▢ ▢ ▢ ▢ │ │  │ repo    │ │ repo    │  …    │ │
│ │ ▢ ▢ ▢ ▢ │ │  └─────────┘ └─────────┘       │ │
│ └──────────┘ └────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

## What it does

- **Browse & search** four kinds of sources in one place
  - **GitHub** — official REST API for repo metadata, README, releases
  - **Official documentation sites** — Read the Docs, GitBook, Docusaurus
    (HTML scrape → Markdown for offline reading)
  - **Generic web search** — DuckDuckGo HTML endpoint, no API key required
  - **Open ebook libraries** — Project Gutenberg's public catalog (70k+ free books)
- **Download** PDF / EPUB / Markdown archives to device storage with progress,
  retry, and resume.
- **Local full-text search** — every downloaded document is indexed in Room
  (FTS4); find a word in seconds across your whole library.
- **Read & annotate** — in-app reader for Markdown / HTML / EPUB with
  highlights, notes, and persisted scroll position.

## Tech stack

- **Kotlin 2.0.21** + **Jetpack Compose** (Material 3, 1.3.x)
- **Hilt** for DI · **Room** (with FTS4) for persistence · **WorkManager** for background downloads
- **Retrofit + OkHttp + kotlinx.serialization** for networking · **Coil** for images
- **Type-safe Navigation Compose** · **Adaptive** layouts (NavigationBar / Rail / Drawer by window size)
- **AGP 8.7.3** · **Gradle 8.10.2** · **minSdk 26** · **targetSdk 35** · **JVM 17**

## Project layout

```
app/src/main/java/me/rerere/readstack/
├── data/
│   ├── source/         — 4 source adapters: GitHub, Docs (RTD/GitBook/Docusaurus), DuckDuckGo, Gutenberg
│   ├── db/             — Room entities, DAOs, FTS4 index
│   ├── download/       — WorkManager-based download manager
│   ├── network/        — OkHttp client factory
│   └── repo/           — LibraryRepository: single API the UI talks to
├── domain/             — Pure-Kotlin models (DocumentRef, SearchResult, Annotation)
├── ui/
│   ├── theme/          — Material 3 theme (indigo + cream, serif headlines)
│   ├── component/      — Reusable Compose widgets (DocumentCard, LibraryCard, …)
│   ├── library/        — My library screen
│   ├── browse/         — Source browser
│   ├── search/         — Search screen (web + local)
│   ├── detail/         — Document detail + download
│   ├── reader/         — Markdown / text reader with annotations
│   └── settings/       — Settings
├── di/                 — Hilt modules
├── App.kt              — @HiltAndroidApp, WorkManager configuration
└── MainActivity.kt     — Edge-to-edge + Compose root
```

## Build

### Prerequisites

- **Android Studio Koala+** (or just the command-line tools)
- **Android SDK 35** installed via SDK Manager
- **JDK 17** (the project uses Java 17 toolchain)
- **Gradle 8.10.2** is provided via the included `gradlew` wrapper

### Steps

```bash
# Clone or copy the ReadStack/ directory, then:
cd ReadStack

# Sanity check the build files parse
./gradlew tasks

# Build a debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Run JVM unit tests (no Android SDK required)
./gradlew :app:testDebugUnitTest
```

In Android Studio: `File → Open` the `ReadStack/` folder, let it sync, then `Run ▶`.

## Extending

### Add a new source

1. Implement `DocumentSource` in `data/source/your/`
2. Bind it in `data/source/DocumentSource` if you add a new `SourceKind` value
3. Wire the adapter in `di/AppModule.kt`
4. Add a new entry to `BrowseUiState.SourceCategory.builtIn`

### Add PDF rendering

The current reader handles Markdown / HTML / plain text + a stubbed EPUB
text extractor. To render PDFs in-app, drop in
[`com.github.barteksc:android-pdf-viewer`](https://github.com/barteksc/AndroidPdfViewer)
or use `android.graphics.pdf.PdfRenderer` + a `HorizontalPager` of `Bitmap`s.

## Known limitations / TODO

- No true resumable downloads (only retry on failure). Adding HTTP `Range` +
  a persisted byte offset is the next step.
- EPUB is parsed with regex (good enough for searching / note-taking; for
  paginated EPUB reading, plug in `epublib4j` or `Readium`).
- No real "global" docs search (RTD / GitBook / Docusaurus don't expose a
  shared index); the docs adapter ships with a 20-entry curated catalogue
  the user can search across. Per-site search is the next addition.
- Material 3 **Expressive** motion / shape-morphing APIs need Material 3
  1.4.x — the project is on 1.3.1 for stability. The theme is set up to
  adopt Expressive by bumping the version.

## License

MIT
