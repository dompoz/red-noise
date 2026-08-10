# AGENTS.md — Red Noise

Android app that generates configurable red (pink/brown) noise for sleep. Single-module Kotlin/Compose project. No tests exist.

## Build & Install

```bash
# Convenience script (runs assembleDebug with the correct JAVA_HOME)
./build.sh

# Subsequent builds (requires local.properties with sdk.dir already set)
JAVA_HOME=/usr/lib/jvm/java-17-temurin ./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release build (minified via ProGuard)
JAVA_HOME=/usr/lib/jvm/java-17-temurin ./gradlew assembleRelease
```

**Important:** The system default Java is 26 (`/usr/bin/java`), which is incompatible with Kotlin 2.0.20 / Gradle 8.7. Always use `JAVA_HOME=/usr/lib/jvm/java-17-temurin` explicitly, or run via `./build.sh` which sets it automatically. Both JDK 17 Temurin (`java-17-temurin`) and Zulu 17 (`zulu-17`) are available at `/usr/lib/jvm/`.

**Requirements:** Java 17 at `/usr/lib/jvm/java-17-temurin`, Android SDK at `~/Android/Sdk`, `adb` installed. The `local.properties` file (not committed) must contain `sdk.dir=~/Android/Sdk`.

**minSdk 33** — no need to handle legacy APIs for audio, notifications, or foreground services.

## Architecture

```
MainActivity  ──binds──►  NoiseService (LifecycleService, foreground)
     │                         │
     │  collectAsState()        │  StateFlow<T>  (isPlaying, volume, redness,
     │◄────────────────────────┘   timerMinutes, timerSecondsRemaining)
     │
     └──►  MainScreen (stateless Compose UI — all state passed as params)
                │
                └── callbacks route back to service methods (play/pause/setVolume/etc.)

NoiseService ──►  SettingsRepository (DataStore Preferences — persists volume, redness, timer)
```

**Key pattern:** `MainActivity` owns the `ServiceConnection`, exposes service `StateFlow`s to Compose via `collectAsState()`, and passes callbacks down. `MainScreen` is fully stateless — it receives values and lambdas only.

The service uses `START_STICKY` and is started with `startService()` before `bindService()` so it survives the activity being destroyed (audio continues in background).

## Audio Engine (`NoiseService`)

The noise is generated entirely in software on a coroutine (`Dispatchers.IO`), written to an `AudioTrack` in stream mode at 44100 Hz mono PCM float.

**Signal chain per sample:**
1. White noise (`Random.nextDouble`)
2. Three cascaded single-pole IIR low-pass filters (same coefficient `α`) — steepens the red slope
3. DC blocker (`y[n] = x[n] - x[n-1] + 0.995·y[n-1]`) — removes low-frequency drift
4. RMS gain rider — normalises to ~0.70 RMS (-3 dBFS), fast attack (~0.1 s), moderate release (~0.5 s)
5. Soft-knee limiter (knee at 0.8, ceiling 1.0) — prevents hard clips

**Redness parameter** maps `[0, 1]` → IIR coefficient `α` in `[0.95, 0.9893]` via `rednessToCoeff()`. Higher α = more bass-heavy / deeper red. The three-pole cascade makes `redness=0` already quite warm; `redness=1` is extremely bass-heavy.

**`@Volatile` fields** `gainVolume` and `filterCoeff` are written from the main thread and read inside the audio loop on IO — `@Volatile` ensures visibility without locking. Changes take effect on the next buffer iteration with no glitch.

**Buffer:** 4096 frames (BUFFER_FRAMES). Actual AudioTrack buffer is `max(minBufferSize, 4096 * 4)` bytes.

## Notification & Media Session

The service uses `MediaStyle` notification (androidx.media compat library, not media2/media3). This gives lock-screen and system media controls. Actions: Play/Pause toggle + Stop.

The notification channel uses `IMPORTANCE_LOW` with `setSound(null, null)` — no sound/vibration for the notification itself.

`PendingIntent` for actions uses `action.hashCode()` as the request code to avoid intent collisions between Play/Pause/Stop actions.

## Sleep Timer

Implemented with `CountDownTimer`. `_timerSecondsRemaining` StateFlow drives the countdown display in the UI ("Stopping in M:SS"). Timer is cancelled and re-created whenever `setTimer()` is called while playing. `timerMinutes=0` means no timer (Off).

## UI

Single screen (`MainScreen`). Notable details:

- Background animates between `background` and `primaryContainer` (800 ms tween) when play state changes.
- Play button has a glow ring (semi-transparent circle behind the button) that appears only when playing.
- Redness slider uses three discrete labels: `< 0.33` → "Deep Red", `< 0.66` → "Deeper Red", `≥ 0.66` → "Ultra Deep".
- Sleep timer uses `FilterChip` grid (row of 4 then row of 3): Off / 15 / 30 / 45 / 60 / 90 / 120 min.
- `SliderRow` is a private composable — label + value label row above a `Slider`.

## Theme

Custom crimson-red Material3 color scheme. Colors defined in `ui/Color.kt` using a Crimson palette seeded from `#D4003A`. Both light and dark schemes are defined (`RedNoiseLightColorScheme`, `RedNoiseDarkColorScheme`) and selected automatically in `RedNoiseTheme` via `isSystemInDarkTheme()`. Do not use `MaterialTheme.colorScheme.primary` assuming it's always red — it is, but verify in `Color.kt` before adding new UI elements.

## File Map

| File | Purpose |
|---|---|
| `MainActivity.kt` | Entry point; service binding; state collection; Compose setup |
| `service/NoiseService.kt` | Audio generation, foreground service, media session, sleep timer |
| `data/SettingsRepository.kt` | DataStore Preferences persistence for volume/redness/timer |
| `ui/MainScreen.kt` | Full UI — `MainScreen`, `SliderRow`, `rednessLabel` |
| `ui/Theme.kt` | `RedNoiseTheme` composable |
| `ui/Color.kt` | Crimson palette + light/dark `ColorScheme` definitions |

## Gotchas

- **No ViewModel.** State lives in `NoiseService` as `StateFlow`s, collected directly in `MainActivity`. Don't introduce a ViewModel without understanding this is intentional (service IS the source of truth).
- **`serviceBound` guards the entire UI.** The `if (serviceBound)` block in `setContent` means `MainScreen` is only composed after the service connects. Avoid putting UI outside this guard.
- **`@Volatile` not `StateFlow` for audio-loop fields.** `gainVolume` and `filterCoeff` are `@Volatile` primitives rather than StateFlows because they're read in a tight audio loop — StateFlow collection overhead would be inappropriate there.
- **No test sources.** There is no `test/` or `androidTest/` directory. Don't assume tests pass or exist.
- **`local.properties` is gitignored** and must be created manually (or via `setup-and-build.sh`) before any Gradle build will succeed.
- **Media compat, not media3.** The project uses `androidx.media` (compat) for `MediaSessionCompat` and `MediaStyle`. Don't mix in `androidx.media3` classes.
