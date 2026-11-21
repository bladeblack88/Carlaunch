# DriveDeck Mobile

Android launcher prototype built with Kotlin and Jetpack Compose.

## Build APK
Every push to `main` and every manual workflow run builds a debug APK.
Open **Actions → Build Android APK** and download the `DriveDeckMobile-debug` artifact.

## Implemented
- Dark dashboard with clock and date
- Reads installed apps that expose a launcher activity
- App picker with saved home-screen selections
- Displays installed app icons and launches apps when tapped
- Three tabs: Home, Apps, Settings
- Android HOME intent filter so it can be selected as the default launcher

## Not implemented yet
- Live music controls and playback metadata
- Live Bluetooth connection status
- Weather and location integration
- Wallpaper and configurable grid/layout
- Widgets and deeper DriveDeck feature parity

Application ID: `com.drivedeck.mobile`
Minimum Android: 8.0 (API 26)
