# UltraBoost Probe 0.1.0

UltraBoost Probe is a small Android diagnostic app for checking which audio-effect engines the device reports and whether selected effects can be attached to a test `AudioTrack` session created by this app.

## What it checks

- Android manufacturer, model, release, and API level.
- Registered Android audio effects: Loudness Enhancer, Bass Boost, Equalizer, and Virtualizer.
- Output devices currently reported by `AudioManager`.
- Whether an effect can be instantiated, controlled, and enabled on the Probe app's own audio session.

## Important limitation

A listed or successfully enabled effect does **not** prove that the effect can process audio from YouTube, games, or every other app. The test attaches effects only to this app's own `AudioTrack` session. Android documents that attaching insert effects to the global output mix with session `0` is deprecated, so this Probe deliberately does not try to force that route.

The test tone is deliberately low-level and Loudness Enhancer is limited to a +3 dB target for this diagnostic. Lower the phone volume before starting, avoid testing with earbuds close to your ears, and stop the tone when finished.

## Build

Requirements:

- JDK 17
- Android SDK with platform 36 and build tools installed
- Network access for the first Gradle/dependency download

From the project directory (the included `gradlew` bootstrap downloads Gradle 8.13 on first run if it is not already cached):

```sh
./gradlew assembleDebug
```

The debug APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

On Windows, open the project in Android Studio with a local Gradle 8.13 installation, or install Gradle 8.13 and run:

```bat
gradle assembleDebug
```

## GitHub Actions

The included workflow at `.github/workflows/android-build.yml` builds the debug APK on pushes to `main` / `master` and when manually started with `workflow_dispatch`. Download the `UltraBoost-Probe-debug` artifact from the workflow run.

## Permissions and privacy

The app requests `MODIFY_AUDIO_SETTINGS` so it can create and control audio effects for its own test session. It does not request microphone, contacts, location, storage, or network permissions. The device report is shared only when the user taps the report button.
