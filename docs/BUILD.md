# Build & Release — Kotlin client

---

## 🛠 Build from Source

### Prerequisites

| Requirement | Version |
|---|---|
| JDK | 17 |
| Android SDK | platform 34, build-tools 34.0.0 |
| Gradle | 8.2.1 (bundled wrapper — no local install needed) |
| Android Studio | Ladybug or newer (optional, CLI works fine) |

The Gradle wrapper is committed, so `./gradlew` works out of the box. The `android/` project is tracked in Git (132 files) — there is **no** `npx cap add android` step and **no** `android-patches/` copy step in this repo.

### 1 — Build a debug APK

```bash
cd android
./gradlew :app:assembleDebug
# -> android/app/build/outputs/apk/debug/app-debug.apk
```

### 2 — Build a release APK

```bash
cd android
./gradlew :app:assembleRelease
# -> android/app/build/outputs/apk/release/app-release.apk
```

### 3 — Install on a device

```bash
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

The Kotlin app installs as `com.spotifydashboard.app`. The React clients install as `com.spotifydashboard.app.ts`, so both can be installed side by side on the same phone.

### 4 — Open in Android Studio

Open the repository root, then run the `app` configuration. The `audio-librespot` module is included automatically (`android/settings.gradle` maps `:audio-librespot` to `../audio-librespot`).

---

## 🔐 Release signing

Signing is opt-in through a **gitignored** properties file at `android/keystore.properties`:

```properties
storeFile=app-release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

- If the file exists, the `release` build type uses it.
- If the file does not exist, Gradle falls back to **debug signing** — the APK still installs, but it is not publishable to Google Play and its key differs from the signed release on the device.
- `android/keystore.properties`, `android/app/app-release.keystore` and `*.keystore` are gitignored. Never commit them.
- CI (`.github/workflows/release.yml`) decodes the keystore from the `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` secrets into the same file, then runs `:app:assembleRelease` and publishes the APK to the GitHub release for `v*` tags.

---

## 🎧 Spotify Developer Setup (do this first)

1. Go to https://developer.spotify.com/dashboard and create an app.
2. In your app settings, add this **Redirect URI** (exact string):
   ```
   com.spotifydashboard.app://callback
   ```
3. Under "APIs used", enable **Web API**.
4. Either paste your **Client ID** into the app (Settings → Spotify credentials) or use the built-in reSpotIt client id.

> **Note:** Since Nov 2025 Spotify requires PKCE — implicit grant is removed. This app uses the PKCE Authorization Code flow via AppAuth.

**Single callback owner:** AppAuth's `RedirectUriReceiverActivity` is explicitly removed from the merged manifest (`tools:node="remove"`) because it claims the same custom scheme as `MainActivity`; Android then shows an app chooser for the redirect, and some ROMs crash on that chooser. `MainActivity` is the only handler of `com.spotifydashboard.app://callback`. The CI `tests.yml` workflow asserts this invariant on every build.
