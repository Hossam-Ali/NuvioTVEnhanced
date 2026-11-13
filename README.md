<div align="center">

  <img src="assets/brand/app_logo_wordmark.png" alt="Nuvio" width="300" />

  <h3>Nuvio TV Enhanced</h3>

  <p>
    An unofficial fork of <a href="https://github.com/NuvioMedia/NuvioTV">Nuvio TV</a> for Android TV and Google TV,
    with AI subtitles in any language, a redesigned look, a home screen you can shape, and an up-to-date build.
    Based on Nuvio TV 1.1.0.
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
perfectly in sync, just not in your language, and the ones that don't still have their audio.

**Nuvio TV Enhanced gives every video subtitles in your language with AI, live, as you watch.** It
translates the video's own subtitles with their original timing, and when a video has none at all,
it listens to the audio and writes them. No searching, no syncing, no wrong-version files.

- **The right subtitle, picked for you.** The video's own subtitle in your language is shown as it
  is. Otherwise its English subtitle is translated (that's what AI translates best), then any other
  language it has, then add-on subtitles.
- **Subtitles made from the audio.** A video with no subtitles at all gets them anyway: the audio is
  read a few minutes ahead, transcribed and translated by AI in one step, and shown in time. They
  catch up within seconds after a seek, and "AI from audio" can be picked for any video.
- **Perfect sync.** Translated lines keep the source's exact timing, and lines made from audio follow
  the speech within a fraction of a second.
- **Natural translations.** Lines are translated together with the dialogue around them by a modern
  language model, not word by word, so they read the way people actually speak.
- **Zero setup per video.** A single **AI translation** entry sits under your language in the
  subtitle menu and is selected by default.
- **Works in both players**, the default one and MPV, and into **any language**.
- **Smooth on screen.** Lines are translated ahead while the video buffers, busy providers are
  retried automatically, and translations are kept on the device, so resuming or rewatching is
  instant and free.
- **Your key, your control.** Works with [OpenRouter](https://openrouter.ai) or OpenAI. Your key is
  entered on the TV, encrypted with the Android Keystore, never synced, and never shipped in the app.
- **Cheap.** GPT-6 Luna translates out of the box (enter any other OpenRouter model if you prefer);
  a full movie usually costs a few cents, and subtitles from audio about $0.05 an hour. Settings
  shows what you spent this month and on the video you're watching.
- **Only when you need them.** A video already spoken in your language isn't translated or
  transcribed; its own subtitle in your language is still shown if it has one.
- **Style presets.** Classic, Cinema, Bold, Yellow, Boxed, Large or Small in one step, then fine-tune
  fonts, background and shadow in Nuvio's appearance editor beside them.

### A redesigned look

- **Everything about a title on one screen.** Info, buttons and cast on one side; seasons and
  episodes on the other, each with its length, IMDb score, a short summary and how far you got.
  Hold OK on an episode to mark it watched.
- **A source list you can read.** The best source sits on top with a big Play button, and every
  source is written as quality, HDR, audio, size and add-on instead of a raw file name. Quick
  filters for 4K, 1080p, 720p and lower, or smallest first.
- **Resume from the home screen.** A Resume button under the hero for whatever you're watching, with
  the time left above its progress bar. It picks up with the same source you last played, and Back
  takes you to the title's page.
- **Your menu, your way.** Icons on the side (hide them until you press left or Back) or tabs across
  the top, in **Settings → Appearance → Layout**.
- **Real artwork on wide cards.** Cards whose add-on only has a poster get the title's wide picture
  from TMDB or the add-on, instead of a poster squeezed into a wide frame.
- **Long descriptions scroll by themselves** on the home screen when you rest on a title, on by
  default (turn it off in **Settings → Appearance → Layout**).
- **English and Arabic**, laid out right to left in Arabic.

### A home screen you shape

- **Visual home editor.** Move, hide and restyle your rows with a live preview beside them, including
  posters or landscape cards per row.
- **Share your setup.** Scan a QR code to save your whole home setup (layout, rows, collections,
  styles and rating badges) from your phone, or send one to the TV; or import one from a link.
- **Ratings at a glance.** With an MDBList key, the hero shows IMDb and Rotten Tomatoes scores
  straight from MDBList, which follows IMDb's own data. Optional rating badges sit on posters, with
  icon packs to change their look.
- **Automatic collection covers** made from a folder's first posters.
- **Titles in their own language.** A show from abroad keeps its original title, logo and
  description on the home screen, Continue Watching, episode names, details, filmography, search
  and the pause screen, instead of a translated title over the original artwork. An episode whose
  only summary is in English shows the show's own description instead.
- **Add-ons that translate every title** (such as ArabCinemeta) no longer rename your rows: an
  English show they call "كروس" appears as "Cross" on the home screen and in search, whatever your
  app language. Titles are looked up once and kept on the device, so rows show the right names
  from the start on later visits.
- **Upcoming episodes aren't blank.** An episode that hasn't aired shows the show's rating and
  description in the hero.
- **Cast on the source list.** See who's in a title before choosing a version, and open any actor's
  films and series.

### Faster and smoother

- **Movie and series pages open sooner.** The page shows as soon as the add-on details arrive, and
  TMDB extras (artwork, localized text, episode details) fill in when ready, instead of waiting for
  every season to load first.
- **Headshots keep the face in view.**
- **Optimized release builds** with the startup profile applied.

### Search that finds what you mean

- **Top results first.** Results from every add-on are merged into one row: each title once, the
  closest match first, and it opens with the add-on that found it. No more scanning the same
  movie across five rows.
- **Any language, original titles too.** Search a show by its Arabic, Turkish, Korean or original
  name and find it, even when your add-ons only know the English title. Results are named in their
  own language, even from add-ons that translate every title.
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
  across more than 300 options.
- **Snappier.** Moving down the list no longer rebuilds every section you pass; a section opens
  when you stop on it or press OK.

### Playback

- **Passthrough formats.** Choose which surround formats (Dolby Digital, Dolby Digital Plus, TrueHD,
  DTS, DTS-HD) go untouched to your TV or receiver; the rest are decoded by the app, for TVs that
  claim a format they can't play.
- **Add-on timeout** you can set, for slow source add-ons.
- **Your audio stays.** The audio track you're listening to is kept when subtitles reload.

### Fixes

- Opening a movie or series no longer crashes after the navigation library upgrade (the page's
  focus state collided with its route arguments).
- The Discover grid can't crash when a movie and a show share an id.
- Search rows from add-ons that fail no longer stay stuck on a loading placeholder.
- A cancelled Trakt refresh no longer makes the next one fail, which could leave Continue
  Watching out of date.
- Playback no longer gets stuck between "starting stream" and "building player" when subtitle
  translation is on.
- Ratings shown in the hero now match the details page, instead of numbers some add-ons report.
- Resuming no longer jumps back a few minutes when the app closed before your position reached
  your account: the newer position on the TV is kept.
- The subtitle button stays in the player when the only subtitles are ones made from the audio.
- Clearing the search field brings back your recent searches again, instead of leaving the last
  results on screen.

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
2. On the TV: **Settings → Subtitles → AI subtitle translation**, turn it on, and enter the key.
3. Choose **Translate to** (any language, or your preferred subtitle language) and the model.
4. Use **Test translation** to check the key, then play anything.

**Automatic AI subtitles** picks the subtitle for you, and **Make subtitles from the audio** covers
videos with none (it needs an OpenRouter key). Both are on by default and can be switched off there.

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
A personal build also applies a one-time playback setup on first start (frame rate matching on, DTS
and DTS-HD decoded by the app, MDBList ratings in the hero); change any of them in Settings afterwards.

## License

[GNU General Public License v3.0](./LICENSE), the same as upstream Nuvio TV.
