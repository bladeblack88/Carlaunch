# DriveDeck Mobile

Android launcher prototype built with Kotlin and Jetpack Compose.

## Build APK
Every push to `main` and every manual workflow run builds a debug APK.
Open **Actions → Build Android APK** and download the `DriveDeckMobile-debug` artifact.

## Current prototype
- Dark mobile dashboard UI
- Clock and date
- Music and Bluetooth placeholder cards
- App-style shortcut grid
- Settings tab
- Android HOME intent filter

## Next implementation steps
- Read installed launcher activities and launch selected apps
- Add configurable shortcuts, wallpaper and grid size
- Integrate media sessions, Bluetooth status, weather and location with runtime permissions

Application ID: `com.drivedeck.mobile`
Minimum Android: 8.0 (API 26)
