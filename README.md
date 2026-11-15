# reSpotIt — Android App (Kotlin)

## About

reSpotIt was built to fill gaps in Spotify's library management. It is an album-centric tool that lets you organize your library and playlists using rich filters, add entire artist discographies to your library or turn them into playlists, import album links from any website, and build queues from albums you are listening to right now. This is a work in progress — expect rough edges and things that may not work yet.

Native Android client. No web server, no Capacitor runtime, no backend required: the library lives on the device and the app works fully offline.

---

## 📥 Installation

Download the latest APK from the [releases page](https://github.com/damianoshar/reSpotIt-kotlin-version/releases/tag/v0.1.3).

1. Download the APK.
2. Install on your Android device (enable "Install unknown apps" if needed).
3. Open the app and sign in with your Spotify account.
4. (Optional) Configure your own Spotify credentials, or the media-server URL, in Settings.

> **Build from source** — prerequisites, Gradle and release signing: [`docs/BUILD.md`](docs/BUILD.md).

---

## 🧱 Stack

- **Language:** Kotlin 1.9
- **UI:** Jetpack Compose (Material 3), single-activity architecture
- **Storage:** Room (SQLite) + OkHttp cache for artwork
- **Networking:** Retrofit 2 + Gson + OkHttp (logging interceptor)
- **Auth:** AppAuth (OAuth 2.0 PKCE) for Spotify, plain bearer tokens for the media server
- **Background work:** WorkManager (retention cleanup, queue switching)
- **Media:** Coil (artwork), AndroidX Media/ExoPlayer-compatible surfaces
- **Toolchain:** JDK 17, Gradle 8.2.1 (wrapper), AGP with `kotlin-kapt`, compileSdk 34, targetSdk 34, minSdk 24 (Android 7.0)
- **App id:** `com.spotifydashboard.app`

---

## reSpotIt Project Structure

reSpotIt is a multi-component project with several variants:

- **Local Server (standalone)** — Python/FastAPI backend + React web frontend + PostgreSQL. Runs entirely on your machine. No AI assistant. See `reSpotIt-local-server-app-version`.
- **Local Server + AI assistant** — Same server plus OpenWebUI, Keycloak, and MCP services for AI-powered library queries. See `reSpotIt-local-server-app-version` (full stack overlay).
- **Kotlin client** (this repo) — Native Android app (Kotlin + Jetpack Compose). Full feature parity with media-server mode.
- **React clients** — Capacitor/TypeScript Android apps. Offer better compatibility on older Android models and low-RAM devices than the Kotlin client. Two variants:
  - **Standard** — Web Playback SDK for browser-based playback (`reSpotIt-react-version`, branch `main`).
  - **Librespot** — Experimental on-device full-track playback via Rust/JNI (`reSpotIt-react-version`, branch `librespot-player`).

> **Note:** All Android clients work fully offline without any server. Media-server mode is optional — enable it in Settings to sync, push/pull, and use the AI assistant.

**Repository:** https://github.com/damianoshar/reSpotIt-kotlin-version (branch: `main`)

---

## 🎮 What you can do

| Tab | What it does |
|---|---|
| **Library** | Album grid/list/compact views, search, rich filters (artist, title, year, genre, date added), sort, selection toolbar, album track sheet, stats bar |
| **Playlists** | Create/edit/delete Spotify playlists, add or remove albums, reorder, visibility toggle, playlist expiry (retention), local queue playlist |
| **Tools** | Stats, Add from site, Scan page with AI (albums), Concerts calendar (month grid with day markers, day filter and card list; a Scan page tab holds the AI scan), AI assistant, Playlist from Spotify search, Artist discography playlist, Find duplicate albums, Follow library artists |
| **Settings** | Media-server mode (server URL, sign-in, sync, push/pull), **phone storage vault** (sign-ins, settings and library survive a reinstall), backup (auto-backup + subfolder), album grid size, sync photos, autohide bottom nav / search bar, CSV import & export, hidden (duplicate) albums, retention, sign out |
| **Player** | Now playing, queue, Web Playback SDK surfaces, experimental on-device librespot player |

---

## 📚 Documentation

| Document | What it covers |
|---|---|
| [`docs/FEATURES.md`](docs/FEATURES.md) | Tabs, AI page capture, data modes, the phone-storage vault |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Source layout, repository tree, PKCE auth flow, data flow |
| [`docs/BUILD.md`](docs/BUILD.md) | Spotify developer setup, build steps, release signing |
| [`docs/TESTING.md`](docs/TESTING.md) | Unit/instrumented tests and CI workflows |
| [`docs/SECURITY.md`](docs/SECURITY.md) | CA trust, LAN access and secret handling |
| [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) | Common failures and fixes |
| [`docs/SCREENS.md`](docs/SCREENS.md) | Every screen, captured from a real device |

---

## 📋 Project notes

- `/api/v1` is a frozen contract: additive changes only.
- Playlist deletion is always explicit and confirmed; hiding a duplicate is reviewable and reversible.
- No Spotify logo is used anywhere — the app is branded reSpotIt.
- The `audio-librespot` module is experimental (seek accuracy, gapless playback, background audio focus). Full tracks require Spotify Premium; the first play opens Spotify's consent page and later starts connect silently. See [`docs/FEATURES.md`](docs/FEATURES.md).
