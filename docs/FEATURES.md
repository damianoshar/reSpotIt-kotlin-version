# Features & behaviour — Kotlin client

What each tab does, plus the technical notes behind the AI scans, the data modes and the phone-storage vault.

---

## 🎮 Features

### Tabs

| Tab | What it does |
|---|---|
| **Library** | Album grid/list/compact views, search, rich filters (artist, title, year, genre, date added), sort, selection toolbar, album track sheet, stats bar |
| **Playlists** | Create/edit/delete Spotify playlists, add or remove albums, reorder, visibility toggle, playlist expiry (retention), local queue playlist |
| **Tools** | Stats, Add from site, Scan page with AI (albums), Concerts calendar (month grid with day markers, day filter and card list; a Scan page tab holds the AI scan), AI assistant, Playlist from Spotify search, Artist discography playlist, Find duplicate albums, Follow library artists |
| **Settings** | Media-server mode (server URL, sign-in, sync, push/pull), **phone storage vault** (sign-ins, settings and library survive a reinstall), backup (auto-backup + subfolder), album grid size, sync photos, autohide bottom nav / search bar, CSV import & export, hidden (duplicate) albums, retention, sign out |
| **Player** | Now playing, queue, Web Playback SDK surfaces, experimental on-device librespot player |

### AI page capture (albums)

`Tools → Scan page with AI (albums)` opens a dialog where you paste a URL (or page text). Press **Open in browser and scan** to load the page in the app's own WebView (`AddFromSiteDialog`), then press **Scan this page for albums (AI)** to snapshot the rendered DOM. The captured text is posted to the media server (`POST /api/v1/llm/extract-albums`), which chunks long pages, extracts `(artist, album)` pairs with the local LLM and resolves them against Spotify. The URL typed in the dialog is preserved when the browser closes.

The same browser offers **Scan this page for concerts (AI)** for concert pages.

### Data modes

- **Local mode (default)** — everything on the device: Spotify PKCE login, saved albums synced into Room, CSV import/export, no server needed.
- **Media-server mode** — point the app at a reSpotIt server (`https://<host>:8000`), sign in with the server account, and sync/push/pull the library. Unlocks the AI assistant, AI page scans and concerts, because the LLM runs on the server.

---

## 💾 Phone storage vault (data survives a reinstall)

Android deletes `/data/data/com.spotifydashboard.app` on uninstall, so a fresh
install used to ask for the Spotify login, the media-server login and the
library all over again. `utils/DataVault.kt` keeps a copy of that private data in
a **public folder** and restores it before the first screen loads.

* **What is mirrored** — `shared_prefs/` (Spotify tokens, server URL + JWT,
  every setting, the librespot credentials) and `databases/` (the Room library).
* **Where it goes** — two destinations, so at least one always works:
  * `Documents/reSpotIt/` — visible in the Files app. Needs **All files access**
    on Android 11+; the *Phone storage* card in Settings opens the system page.
  * `Android/media/com.spotifydashboard.app/reSpotIt/` — no permission required,
    also survives an uninstall, just less discoverable.
* **How the restore picks a copy** — the newest `vault-info.txt` marker wins.
  The marker is written last, so a half-finished copy is never treated as valid.
* **When it runs** — `ReSpotItApp` (the Application class) restores on start,
  before the first Activity opens any SharedPreferences or Room database (so the
  app comes up already signed in), and saves on `onActivityStopped` from a
  background thread. Settings → **Phone storage** shows the saved copy and the
  file count, with *Save now* and *Save to Documents* (the latter needs *All
  files access*).

> **Security:** the public copies contain the Spotify refresh token and the
> server JWT **in plaintext**, readable by any app with storage access. They are
> never uploaded anywhere and are excluded from Git. *Sign out* clears the
> private data, but the last vault copy stays until the next save — delete
> `Documents/reSpotIt` by hand on a shared device.
