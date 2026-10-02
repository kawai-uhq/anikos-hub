# Aniko's Hub

Android starter for browsing TMDB movie/TV metadata and opening configured provider embed players.

## Build from an Android tablet with GitHub Actions
1. Create a new GitHub repository.
2. Upload **the contents of this folder** to the repository root. Make sure `.github/workflows/build-apk.yml` is included.
3. Open the repository's **Actions** tab and select **Build Aniko's Hub APK**.
4. Tap **Run workflow**. (A push to `main`/`master` also triggers a build.)
5. When the run is green, open it and download the `Anikos-Hub-debug-apk` artifact.
6. Extract the downloaded artifact ZIP and install `app-debug.apk` on Android. You may need to allow your browser/files app to install unknown apps.

## TMDB
After installation, open **Settings** inside Aniko's Hub and add your TMDB API Read Access Token. Do not commit the token to GitHub.

TMDB attribution: This product uses the TMDB API but is not endorsed or certified by TMDB.
