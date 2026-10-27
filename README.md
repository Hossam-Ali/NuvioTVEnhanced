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

### Watch anything with subtitles in your language

Finding subtitles that match your exact video is a hunt: the right release, the right frame rate,
and still a line that shows up a second late. But most videos already carry subtitles that are
perfectly in sync, just not in your language.

**Nuvio TV Enhanced translates those built-in subtitles with AI, live, as you watch.** You get
subtitles in your own language with the original's timing, on any movie or episode that ships with
text subtitles. No searching, no syncing, no wrong-version files.

- **Perfect sync, every time.** Only the subtitle text goes to the AI; every line keeps its original
  timing, so the translation is exactly as in sync as the source.
- **Natural translations.** Lines are translated together with the dialogue around them by a modern
  language model, not word by word, so they read the way people actually speak.
- **The best source, picked for you.** It translates the subtitle in the film's spoken language
  (a Korean film uses its Korean subtitle), falling back to English, then any text subtitle.
- **Zero setup per video.** A single **AI translation** entry sits under your language in the
  subtitle menu and is selected by default.
- **Add-on subtitles too.** When a video has no subtitles of its own, an add-on subtitle in the
  film's language (or English) is translated instead, ahead of time, so lines are ready before
  they appear. Pick any add-on subtitle in another language and it's translated as well.
- **Works in both players**, the default one and MPV.
- **Any language.** Translate into whichever language you watch in.
- **Smooth on screen.** Lines are translated ahead while the video buffers, so they're ready before
  they appear. Busy providers are retried automatically, a late line is held for a moment instead
  of flashing the original, and translations are kept on the device, so resuming or rewatching is
  instant.
- **Your key, your control.** Works with [OpenRouter](https://openrouter.ai) or OpenAI. Your key is
  entered on the TV, encrypted with the Android Keystore, never synced, and never shipped in the app.
- **Fast and accurate by default.** GPT-6 Luna translates out of the box; enter any other OpenRouter
  model if you prefer, and Luna steps in when it's busy. A full movie usually costs a few cents.
- **Know what you spend.** Settings shows what translation cost this month and for the video
  you're watching.

### Faster and smoother

- **Movie and series pages open sooner.** The page shows as soon as the add-on details arrive, and
  TMDB extras (artwork, localized text, episode details) fill in when ready, instead of waiting for
  every season to load first.
- **Original titles and logos** on the Modern home layout, shown in each title's own language, and
  cards show the whole poster instead of cropping it.
- **Optimized release builds** with the startup profile applied.

### Search that finds what you mean

- **Top results first.** Results from every add-on are merged into one row: each title once, the
  closest match first, and it opens with the add-on that found it. No more scanning the same
  movie across five rows.
- **Any language, original titles too.** Search a show by its Arabic, Turkish, Korean or original
  name and find it, even when your add-ons only know the English title.
- **Search by actor.** An "Actors & crew" row takes you straight to a person's movies and series.
- **Clean, fast results.** Add-ons that fail to answer disappear instead of loading forever, and
  slow ones join in when ready rather than filling the screen with placeholders.

Searching by original title and by actor uses TMDB: add your free TMDB key in
**Settings → Sources → Integrations → TMDB**.

### Settings that make sense

- **Seven clear sections** instead of a long list: Account, Appearance, Playback, Subtitles,
  Sources, Tracking and System. Related pages sit together as tabs, such as
  Theme and Layout under Appearance.
- **Subtitles have their own section**, with languages, style, AI translation and sync in one place.
- **Search your settings.** Type a word, in English or Arabic, and jump straight to the setting,
  across almost 300 options.
- **Snappier.** Moving down the list no longer rebuilds every section you pass; a section opens
  when you stop on it or press OK.

### Fixes

- Opening a movie or series no longer crashes after the navigation library upgrade (the page's
  focus state collided with its route arguments).
- The Discover grid can't crash when a movie and a show share an id.
- Search rows from add-ons that fail no longer stay stuck on a loading placeholder.
- A cancelled Trakt refresh no longer makes the next one fail, which could leave Continue
  Watching out of date.
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
| OkHttp / Retrofit                   | 4.12 / 2.9              | 5.5 / 3.0          |

The build uses the Gradle plugin's built-in Kotlin support. The video player core (Nuvio's custom
Media3 build in `app/libs`) is unchanged.

### Kept up to date

Every push is built and tested on GitHub, and once a week the fork checks for new upstream Nuvio
TV changes and opens a pull request with them.

## Install

1. Open the [latest release](https://github.com/Hossam-Ali/NuvioTVEnhanced/releases/latest) and download the APK for your device:
   - `armeabi-v7a`: most Google TV devices, including the Google TV Streamer and Chromecast with Google TV
   - `arm64-v8a`: devices running 64-bit Android TV, such as the NVIDIA Shield
   - `universal`: works everywhere, but larger
2. Install it with a downloader app or `adb install`.

It installs as **Nuvio Enhanced** (`com.nuvio.tv.enhanced`), next to the official Nuvio app, and its
updater checks this repository's releases.

> Release APKs are built without third-party service keys. Add-ons, catalogs, playback and AI
> subtitles work without them. Add your own free TMDB key in Settings for artwork, ratings,
> original-title search and actors. Trakt, Simkl, MDBList and Nuvio account sync need a build with
> your own keys (see below).

## Set up AI subtitles

1. Create a key at [openrouter.ai](https://openrouter.ai/keys) (or OpenAI).
2. On the TV: **Settings → Playback → Subtitles → AI subtitle translation**, turn it on, and enter the key.
3. Choose **Translate to** (any language) and the model.
4. Use **Test translation** to check the key, then play something with built-in subtitles.


## Build from source

```bash
git clone https://github.com/Hossam-Ali/NuvioTVEnhanced.git
cd NuvioTVEnhanced
./gradlew :app:assembleFullDebug       # debug build
./gradlew :app:assembleFullBenchmark   # optimized build signed with the debug key
```

Requires JDK 17+ and the Android SDK (platform 37). Optional service keys go in `local.properties`;
see [`local.example.properties`](./local.example.properties). Never commit `local.properties`.

For your own device, you can build your OpenRouter key into the app so you don't type it on the TV.
Add it to `local.properties` and build the debug or benchmark variant:

```properties
OPENROUTER_API_KEY=sk-or-v1-...
SUBTITLE_TRANSLATION_TARGET=en   # default "Translate to" language
```

Release builds never include the key. Personal builds do (masked, but extractable), so don't share them.

## License

[GNU General Public License v3.0](./LICENSE), the same as upstream Nuvio TV.
