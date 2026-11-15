# Architecture — Kotlin client

Source layout, authentication and data flow.

---

## 🗺️ App architecture

```
android/app/src/main/java/com/spotifydashboard/app/
├── MainActivity.kt              ← single activity, Compose host, AppAuth redirect receiver
├── ReSpotItApp.kt               ← Application: vault restore on start + backup on activity stop
├── api/                         ← Retrofit services + DTOs
│   ├── DashboardApiService.kt   ← media-server /api/v1 client (albums, playlists, LLM, jobs)
│   ├── SpotifyApiService.kt     ← Spotify Web API client
│   ├── SpotifyPlaylistApiService.kt
│   ├── DashboardApiClient.kt    ← OkHttp/Retrofit factory (long-timeout LLM client)
│   └── SpotifyApiClient.kt
├── auth/SpotifyAuthManager.kt   ← AppAuth PKCE flow, token refresh, persistence
├── data/                        ← Room
│   ├── AppDatabase.kt
│   ├── Entities.kt              ← albums, playlists, tracks, links, history
│   ├── Daos.kt
│   └── ViewMode.kt              ← grid/list/compact view modes
├── playback/                    ← experimental on-device librespot playback
│   ├── LocalPlaybackController.kt
│   └── LocalPlaybackService.kt  ← foreground media service
├── repository/                  ← data access used by the ViewModels
│   ├── DashboardRepository.kt   ← local + remote library, LLM scans, sync
│   ├── SpotifyRepository.kt
│   ├── PlaylistRepository.kt
│   └── SyncCancel.kt
├── ui/                          ← Compose screens and dialogs
│   ├── DashboardScreen.kt       ← root scaffold, tabs, Tools/Settings
│   ├── MainViewModel.kt         ← library, playlists, AI scans, queue
│   ├── LibraryViewModel.kt
│   ├── AddFromSiteDialog.kt     ← in-app browser (WebView) + page snapshot
│   ├── AiUrlScanDialog.kt       ← "Scan page with AI (albums)"
│   ├── ConcertsDialog.kt / ConcertsListDialog.kt
│   ├── AiAssistantDialog.kt / AiAssistantState.kt
│   ├── DuplicatesDialog.kt
│   ├── RetentionDialog.kt
│   ├── SpotifyCredentialsScreen.kt
│   ├── PhoneStorageSection.kt   ← Settings → "Phone storage" (vault status + actions)
│   └── …
├── utils/                       ← pure helpers (CSV, duplicates, queue planner, AI chat)
│   └── DataVault.kt             ← mirrors shared_prefs + databases into the public folder
└── workers/                     ← WorkManager: RetentionWorker, QueueSwitchWorker
```

Native module (outside `android/`): `audio-librespot/` — Rust librespot core + Kotlin JNI bridge (`LibrespotBridge.kt` → `LibrespotEngine.kt` → `PcmPlayer.kt`), prebuilt `.so` files under `audio-librespot/src/main/jniLibs/<abi>/`.

---

## 📦 Repository layout

```
android/                  ← the Gradle project (tracked, 132 files)
├── app/                  ← application module (Compose UI, Room, Retrofit)
├── gradle/wrapper/       ← Gradle 8.2.1 wrapper
└── settings.gradle       ← includes :app, :audio-librespot, capacitor plugins
audio-librespot/          ← Rust librespot core + Kotlin JNI bridge (module :audio-librespot)
android-patches/         ← reference copies of the manifest/MainActivity/network config
public/, index.html       ← web landing page (not part of the app build)
docs/                    ← landing-page assets + SCREENS.md (screen guide with screenshots)
docs/screenshots/        ← device screenshots used by the screen guide
```

> **Legacy leftovers:** `package.json`, `capacitor.config.json`, `vite.config.ts`, `tsconfig.json` and `android/app/src/main/assets/public/` came from the earlier Capacitor prototype. This app is fully native — the app does **not** load a WebView bundle, and those files are not part of the Gradle build.

---

## 🔐 Auth flow (PKCE)

```
User taps "Connect with Spotify"
        ↓
Chrome Custom Tab opens Spotify login
        ↓
User approves → Spotify redirects to:
  com.spotifydashboard.app://callback?code=XXX&state=YYY
        ↓
MainActivity (singleTask) catches the deep link → App resumes
        ↓
AppAuth exchanges code + verifier for access + refresh tokens
        ↓
Tokens stored in the app's private SharedPreferences (mirrored to the public
vault folder — see FEATURES.md)
        ↓
Auto-refresh when the token expires (55 min cycle)
```

The PKCE verifier/state survive process death: they are written to disk before the browser opens, so an OS kill in the background does not break the callback.

---

## 🗺️ Data flow

```
Spotify Auth (PKCE)
      ↓
SpotifyApiService → GET /me/albums (paginated) + /artists genre enrichment
      ↓
SyncService → CSV stage → artwork download (Filesystem cache)
      ↓
Room: albums / playlists / tracks
      ↓
MainViewModel → Compose UI

Media-server mode:
  Server (FastAPI)  ⇄  DashboardApiService  →  Room (push/pull)
                              ↓
                    POST /api/v1/llm/extract-albums (AI page scan)
```
