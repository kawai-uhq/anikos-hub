# Aniko's Hub

Android app for browsing TMDB movie/TV metadata and opening configured provider embed players.

## Features

- **Browse** trending titles, popular movies & TV
- **Search** multi-type (movies + shows)
- **Detail** view with seasons/episodes for TV
- **Favorites** (local, offline)
- **Multiple embed providers** (VidLink, CineSrc, VidFast) with preferred-provider setting
- **In-app WebView player** with landscape + immersive mode, reload, and open-in-browser
- **Auto update check** against GitHub Releases
- **TMDB token** stored locally in Settings

## Architecture (v2)

```
app/src/main/java/com/anikoshub/app/
├── MainActivity.kt          # Thin entry + navigation state
├── data/
│   ├── Models.kt            # Media, Episode, Provider, UiState
│   ├── TmdbClient.kt        # TMDB API client
│   └── AppPreferences.kt    # Token, favorites, preferred provider
├── ui/
│   ├── theme/Theme.kt
│   ├── components/          # Poster, MediaGrid, EpisodeCard, Loading/Error
│   └── screens/             # Main, Detail, Player, Settings
└── util/
    └── UpdateChecker.kt
```

## Build from an Android tablet with GitHub Actions

1. Create a new GitHub repository.
2. Upload **the contents of this folder** to the repository root. Make sure `.github/workflows/build-apk.yml` is included.
3. Open the repository's **Actions** tab and select **Build Aniko's Hub APK**.
4. Tap **Run workflow**. (A push to `main`/`master` also triggers a build.)
5. When the run is green, open it and download the `Anikos-Hub-debug-apk` artifact.
6. Extract the downloaded artifact ZIP and install `app-debug.apk` on Android. You may need to allow your browser/files app to install unknown apps.

## Local build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+.

## TMDB

After installation, open **Settings** inside Aniko's Hub and add your TMDB API Read Access Token. Do not commit the token to GitHub.

TMDB attribution: This product uses the TMDB API but is not endorsed or certified by TMDB.

## Changelog (upgrade)

- Split monolithic `MainActivity` into packages (`data`, `ui`, `util`)
- Consistent package `com.anikoshub.app`
- Typed `UiState` for loading / error / success
- Favorites tab with local persistence
- Configurable providers with display names + preferred provider in Settings
- Popular movies section on Home
- Landscape + immersive mode while playing
- Retry buttons on error states
- Cleaner components and theme
