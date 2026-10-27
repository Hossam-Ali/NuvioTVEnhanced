<div align="center">

  <img src="assets/brand/app_logo_wordmark.png" alt="Nuvio" width="300" />

  <h3>Nuvio TV Enhanced</h3>

  <p>
    An unofficial fork of <a href="https://github.com/NuvioMedia/NuvioTV">Nuvio TV</a> for Android TV and Google TV,
    with AI subtitle translation, faster screens, and an up-to-date build.
  </p>

[Download the latest release](https://github.com/Hossam-Ali/NuvioTVEnhanced/releases/latest) · [Upstream Nuvio TV](https://github.com/NuvioMedia/NuvioTV) · [Report an issue](https://github.com/Hossam-Ali/NuvioTVEnhanced/issues)

</div>

> This fork is not affiliated with or supported by the Nuvio team. All credit for Nuvio TV goes to
> [NuvioMedia](https://github.com/NuvioMedia) and its contributors. For the official app, use the
> [upstream releases](https://github.com/NuvioMedia/NuvioTV/releases/latest).

## Why "Enhanced"?

It started with one problem: watching with Arabic subtitles meant searching subtitle add-ons for a
file that matched the exact video, and they were often out of sync. Videos usually carry their own
subtitles, which are perfectly in sync, just in another language. This fork translates those
subtitles with AI and keeps their timing, then fixes the speed problems found along the way.

### AI subtitle translation

- **Perfect sync.** The video's own (embedded) subtitle is translated line by line. Only the text is
  sent to the AI, so every line keeps its original timing.
- **The right source.** It translates the subtitle in the film's spoken language: a Turkish film
  uses its Turkish subtitle, a French film its French one. If there isn't one, English is used,
  then any other text subtitle.
- **One clear choice.** The subtitle menu shows a single **AI translation** entry under your
  language. It's selected by default, even when an Arabic track or add-on subtitle exists.
- **Falls back cleanly.** Videos with no text subtitles (or only image subtitles such as PGS) use
  your add-on subtitles, and the app tells you why.
- **Your own key.** Works with [OpenRouter](https://openrouter.ai) or OpenAI. You enter your key on
  the TV. It's encrypted with the Android Keystore, never synced, and never shipped in the app.
- **Two models.** Ling 3.0 Flash (default, cheapest) or GPT-6 Luna (faster and more accurate in our
  tests). Each one takes over automatically when the other is busy.
- **Smooth on screen.** Lines are translated in batches while the video buffers ahead. The app backs
  off and retries on rate limits, holds a line briefly instead of flashing the original, and keeps
  translations on the device, so resuming or rewatching is instant.

### Faster and smoother

- **Movie and series pages open sooner.** The page shows as soon as the add-on details arrive, and
  TMDB extras (artwork, localized text, episode details) fill in when ready, instead of waiting for
  every season to load first.
- **Original titles and logos** on the Modern home layout (for example Arabic logos for Arabic
  shows), and cards show the whole poster instead of cropping it.
- **Optimized release builds** with the startup profile applied.

### Fixes

- Opening a movie or series no longer crashes after the navigation library upgrade (the page's
  focus state collided with its route arguments).
- The Discover grid can't crash when a movie and a show share an id.
- Playback no longer gets stuck between "starting stream" and "building player" when subtitle
  translation is on.

### Up to date

|                                     | Upstream                | Enhanced           |
| ----------------------------------- | ----------------------- | ------------------ |
| Gradle                              | 8.13                    | 9.8.2              |
| Android Gradle plugin               | 8.13.2                  | 9.4.1              |
| Compile SDK                         | 36                      | 37                 |
| Compose                             | 1.11.2                  | 1.12.1             |
| Kotlin                              | 2.3.0                   | 2.4.21             |
| Hilt                                | 2.58                    | 2.60.1             |
| Coil                                | 3.3.0                   | 3.6.3              |
| Coroutines / Lifecycle / Navigation | 1.8 / 2.8 / 2.8         | 1.11 / 2.11 / 2.10 |
| TV Material / TV Foundation         | 1.1.0-rc01 / 1.0.0-rc01 | 1.1.0 / 1.0.0      |

The video player core (Nuvio's custom Media3 build in `app/libs`) is unchanged.

## Install

1. Open the [latest release](https://github.com/Hossam-Ali/NuvioTVEnhanced/releases/latest) and download the APK for your device:
   - `armeabi-v7a`: most Google TV devices, including the Google TV Streamer and Chromecast with Google TV
   - `arm64-v8a`: devices running 64-bit Android TV, such as the NVIDIA Shield
   - `universal`: works everywhere, but larger
2. Install it with a downloader app or `adb install`.

It installs as **Nuvio Enhanced** (`com.nuvio.tv.enhanced`), next to the official Nuvio app, and its
updater checks this repository's releases.

> Release APKs are built without third-party service keys (TMDB, Trakt, Simkl, MDBList, Nuvio account
> sync). Add-ons, catalogs, playback and AI subtitles work without them. To use those services,
> build the app yourself with your own keys (see below).

## Set up AI subtitles

1. Create a key at [openrouter.ai](https://openrouter.ai/keys) (or OpenAI).
2. On the TV: **Settings → Playback → Subtitles → AI subtitle translation**, turn it on, and enter the key.
3. Choose **Translate to** (Arabic, or any language) and the model.
4. Use **Test translation** to check the key, then play something with built-in subtitles.

A full movie usually costs a few cents with Ling 3.0 Flash.

## Build from source

```bash
git clone https://github.com/Hossam-Ali/NuvioTVEnhanced.git
cd NuvioTVEnhanced
./gradlew :app:assembleFullDebug       # debug build
./gradlew :app:assembleFullBenchmark   # optimized build signed with the debug key
```

Requires JDK 17+ and the Android SDK (platform 37). Optional service keys go in `local.properties`;
see [`local.example.properties`](./local.example.properties). Never commit `local.properties`.

## License

[GNU General Public License v3.0](./LICENSE), the same as upstream Nuvio TV.
