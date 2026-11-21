# DriveDeck Mobile

Android automotive-style launcher built with Kotlin and Jetpack Compose.

## Build APK
Every push to `main` and every manual workflow run builds a debug APK.
Open **Actions → Build Android APK** and download the `DriveDeckMobile-debug` artifact.

## Implemented in the current development build
- Dark dashboard with live clock and date
- Installed app discovery, favorites, saved selections and app launching
- Configurable app grid (3–5 columns)
- Current weather from Open-Meteo using the device location (location permission required)
- Bluetooth adapter status and shortcut to Android Bluetooth settings (Bluetooth permission required on Android 12+)
- Active media session metadata and playback controls (notification-listener access must be enabled in Android settings)
- Wallpaper settings shortcut
- Shortcut to Android's default home-app settings

## Notes
- Music integration uses Android active media sessions. Some music apps or device vendors may expose limited metadata or playback controls.
- Weather requires internet and location access. It uses Open-Meteo.
- Bluetooth currently reports adapter state; it does not yet enumerate connected audio devices.
- This is an evolving launcher, not a full clone of every DriveDeck feature.

Application ID: `com.drivedeck.mobile`
Minimum Android: 8.0 (API 26)
