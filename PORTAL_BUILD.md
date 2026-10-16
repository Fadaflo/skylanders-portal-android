# Skylanders Portal for Android

This branch contains the corresponding source for the Portal preview APK
dated 2026-10-17, including the console UI, controller and graphics profiles,
game-document import, generated artwork and packaged catalogue assets.
It is based on Dolphin commit `81d6e0919f` with the Portal working-tree changes.

## Build

1. Clone this branch with submodules:
   `git clone --recurse-submodules --branch skylanders-android https://github.com/Fadaflo/skylanders-portal-android.git`
2. Install Android Studio, Android SDK platform 37, NDK 30.0.15729638 and
   a compatible CMake (3.25 or newer). Use Java 17 or a compatible Studio JBR.
3. Open `Source/Android` in Android Studio and configure the local SDK path.
4. From that directory run `./gradlew :app:assembleDebug` (Windows:
   `gradlew.bat :app:assembleDebug`). The debug variant has a separate package.

For a release build supply your own keystore through the Gradle properties
`keystore`, `storepass`, `keyalias`, `keypass`, then run `:app:assembleRelease`.
No signing key or private app data is included in this repository.

The packaged `app/src/main/assets/skylanders` catalogue is included, so the
original asset-generation workspace is not required. Menu cue synthesis is
documented in `Source/Android/create_menu_sounds.py`.

## Behaviour and limits

The app includes an emulator and digital portal, not Wii game files. It links
user-selected local files. Save data and figures remain local. Account login,
cloud sync and automatic app updates are not implemented in this preview.

Android/OpenGL graphics profiles use synchronous specialised shaders because
the tested Adreno driver crashes with background shader compilation. Missing
optional Wii network certificates still produce SSL errors; for Android
Skylanders games using the emulated portal, absent certificates do not open a
blocking panic dialog. Existing invalid certificate warnings and TLS verification
remain unchanged.

## Licences and attribution

See `COPYING`, `LICENSES`, per-file SPDX tags and third-party component licences.
The code licences do not confer rights to Skylanders names, graphics or music.
Game artwork and menu music were supplied for this fan project. The Spyro app
icon is AI-generated; the font licence is packaged in `res/raw`.

The APK was built from a working tree with the base Git version metadata above;
the source snapshot includes the same application code and resources. Rebuilding
with a different signing key or Git commit changes binary/signature metadata.
