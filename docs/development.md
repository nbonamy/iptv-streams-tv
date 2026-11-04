# Development

The root project has two modules. `core/` owns M3U parsing, stable channel identity,
metadata joins, country channel ordering, filtering, the bounded source transport, retry policy, and the
phone-search HTTP server. `app/` owns native TV UI, Media3, local preferences, the
atomic library cache, QR rendering, and lifecycle.

## Build and checks

Use JDK 17 and Android SDK 36. Set `ANDROID_HOME`, or put your SDK path in ignored
`local.properties`. The Makefile defaults to the standard macOS SDK and JDK paths.

```sh
make check
make build
./gradlew :app:assembleDebugAndroidTest
```

`make check` runs core tests, Android unit tests, lint and the debug build. Unit
tests exercise country/category joins, variants and headers, malformed input,
favorites, logo parsing, country lineup ordering, prepared indexes, offline startup and failed-cache replacement.
The copied phone-server tests cover request decoding, escaping, invalid requests,
port conflicts and restart.

## Emulator acceptance

Use an explicitly selected Android TV emulator. Do not install on a physical TV
without Nicolas' instruction. Installation and launching are separate commands:

```sh
make install ANDROID_DEVICE=emulator-5554
make run ANDROID_DEVICE=emulator-5554
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  -e class fr.bonamy.iptv.BrowsingTest,fr.bonamy.iptv.SearchTest,fr.bonamy.iptv.HeaderMenuTest,fr.bonamy.iptv.RemoteKeyboardTest \
  fr.bonamy.iptv.test/androidx.test.runner.AndroidJUnitRunner
```

`BrowsingTest` verifies favorites/recreation, search, France's first channels,
six-column portrait card geometry, logo/name placement, home-only hero,
country selection and the France / USA /
UK / Canada home priorities through the real native UI. `SearchTest` verifies
empty queries, a hidden search hero, and country context on results.
`HeaderMenuTest` verifies remote OK on the IPTV/breadcrumb header
opens the shared custom action sheet, with Refresh available only in that menu. `RemoteKeyboardTest`
decodes the displayed QR bitmap, submits a query through that endpoint, verifies
TV results, and checks listener shutdown/restart across activity visibility.

Run live acceptance explicitly when the emulator has network access:

```sh
adb -s emulator-5554 shell am instrument -w \
  -e class fr.bonamy.iptv.LivePlaybackTest \
  fr.bonamy.iptv.test/androidx.test.runner.AndroidJUnitRunner
```

This selects France 24 from the built-in library, requires rendered video frames
and advancing playback, then verifies layered Back and return to the same search.
It also requires refresh/cache completion while video is decoding, using compact
metadata and streaming cache writes within the normal TV heap. It is a live-source
probe, not an availability certificate for every channel.

Playback regression and sustained-source probes use the same Media3 player as
the app:

```sh
adb -s emulator-5554 shell am instrument -w \
  -e class fr.bonamy.iptv.ShortLiveWindowTest \
  fr.bonamy.iptv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w \
  -e class fr.bonamy.iptv.StreamStabilityTest \
  -e channelId TF1.fr -e sampleSeconds 120 -e allowDecoderFallback true \
  fr.bonamy.iptv.test/androidx.test.runner.AndroidJUnitRunner
```

`ShortLiveWindowTest` serves original synthetic H.264/AAC segments from a local
HTTP origin with an eight-second sliding window and a delayed first request.
It requires prompt startup and continuously advancing frames without player errors.
`StreamStabilityTest` records timing, formats, decoder names, bytes, buffering and
audio/video frame counts in private `files/playback-trace.jsonl`; it records no source URLs
or headers. It requires rendered frames, no player errors and no frame stall of
five seconds. `channelId`, `sampleSeconds` (15–180), `streamUrl`, `disableAudio`
and `software` select explicit probes. Omit `software` to exercise ordinary
hardware-first playback. `allowDecoderFallback=true` permits one decoder error
only if FFmpeg video subsequently initializes and the picture recovers within
ten seconds. This handoff is reported separately as `decoderRecoveryGap`;
steady playback retains the five-second stall limit. Other errors fail the probe.
The probe also requires selected audio to render. The Nextlib Media3 extension,
pinned to Media3 1.11.1, supplies FFmpeg audio/video decoders in all builds.
Hardware decoding has priority; unsupported audio uses FFmpeg automatically.
A runtime decoder failure immediately retries the same stream with FFmpeg
preferred, within the existing retry budget. Source selection resets decoder
preference. Decoder failure recovery does not launch an external player.

HLS media segments of 256 KiB–8 MiB use four bounded parallel HTTP ranges when
the origin proves range support. Ranges must match the requested positions,
total length and supplied entity validator before any bytes reach Media3.
Unsupported or inconsistent origins use the normal streaming transport. The
shared budget is 16 MiB and eight workers; manifests, keys, DASH and progressive
streams retain their normal transport. Source headers and requested byte ranges
are preserved, and cancellation closes outstanding requests. Unit tests cover
exact bytes, concurrent requests, malformed responses, fallback and cancellation.

Short sliding live windows whose default start is within the oldest segment
start halfway through the available window. Explicit provider start offsets,
safe defaults and long DVR windows are preserved. A behind-live-window error
seeks within the current live window and prepares the same Media3 instance,
using the normal bounded reconnection policy.

## Source and storage

Built-in source: `https://iptv-org.github.io/iptv/index.m3u`.
Metadata: `https://iptv-org.github.io/api/channels.json`.
The bundled snapshots under `app/src/main/assets/` were retrieved November 3, 2026.
No user playlist/account entry is provided. A successful refresh validates before
replacing the local cache through Android AtomicFile. A failed refresh leaves the
working catalog intact. Invalid caches fall back to the bundled source.

Startup and country navigation never download the playlist. `Refresh` in the IPTV
header menu is the only catalog download action. The build generates `catalog.json`
from the bundled M3U and metadata using the core parser. A first install seeds the
local catalog from this prepared data and persists it after the UI is ready;
an older text-only cache upgrades once.
The versioned atomic cache retains both source text and parsed channels. A shared
application store reuses its channel snapshot across activity recreation. Parsed
channels precede source text in the cache, so startup does not decode the retained
M3U and metadata strings. Source text is loaded only when needed by Refresh.
Country lists, lineup ordering, ID lookups and folded search text are prepared once
per catalog load or successful refresh. Favorites read only their saved IDs;
channel metadata always comes from the source.

`CatalogPerformanceTest` checks local startup without downloads, first-install
seeding, and three real France grid draws (under 250 ms). Run it alongside the
native browsing/search checks. Generated catalog output stays under `build/` and
is regenerated when source snapshots or the core parser change.

Country/category joins use the provider channel ID before its variant suffix.
Unknown channels remain available. Favorites persist by channel ID; channels
without an ID use a deterministic URL hash and may acquire a new identity if that
URL changes. Removed favorite IDs remain saved and appear as unavailable until
the source restores them or the viewer removes them. Stream selection is explicit;
recovery retries the same stream three times with bounded backoff.

HTTP and HTTPS streams are supported through Media3, including HLS, DASH and
progressive media recognized by its source factory. Cleartext is enabled because
the chosen public playlist contains HTTP streams. Unsupported URL schemes are
not playable. Stream headers are limited to the source's user agent, referrer and
origin. No DRM, catch-up, worldwide EPG, recording, multiview, or source credentials UI is added.

## Country channel order

`app/src/main/assets/channel-order.json` supplies optional lineups keyed by
country code. Numbered entries contain source provenance and a `channels` array:

```json
{"number": 1, "name": "TF1", "ids": ["TF1.fr"], "aliases": []}
```

The core ranks matching stable provider IDs first, with exact normalized names or
explicit aliases as fallback within that country. Listed channels sort by number;
unlisted channels retain catalog order (alphabetical). Missing entries do not
create channels. Favorites retain their manual order, and global search stays
alphabetical. There are no country-specific branches in the ordering code.

France's initial lineup uses the first national DTT table for metropolitan France
in [Wikipedia's list](https://en.wikipedia.org/wiki/List_of_television_stations_in_France).
Regional number ranges are omitted. Source URL, revision and retrieval date are
recorded alongside the data. Update the bundled lineup explicitly, then review
the resulting data and run `make check`:

```sh
python3 tools/update-country-order.py --country FR \
  --url https://en.wikipedia.org/wiki/List_of_television_stations_in_France
```

Other countries can use the same schema. The import tool supports numbered
Wikipedia tables whose first two columns identify channel number and name;
`--number-column` and `--name-column` select their headers. Runtime refresh updates
the playlist and metadata, leaving bundled lineup policy unchanged.

USA uses an editorial grouping policy in the same asset. Its ordered `groups`
array defines mainstream, news, movies, shows, music, factual, sports and kids.
Each group has a stable `key` and an ordered `ids` list of familiar networks.
News includes the main national channels plus Chicago. Genre metadata does not
pull additional channels into the focused groups. Every remaining channel,
including other local news, follows the entire focused lineup in catalog order
(alphabetical). The UI stays one continuous grid with no headings or dividers.
Unlisted and missing IDs never create channels.

The generic schema also accepts optional `categories` and `categoryPriority` for
countries using genre-based fallback. Duplicate group keys, IDs or unknown
priority keys are rejected. Numbered and grouped policies are mutually exclusive.
Both preserve Favorites, global search and available stream choices.

The US policy's `excludedIds` is derived from provider feed-language metadata;
`retainCategories: ["sports"]` keeps Spanish sports. Explicit language overrides
cover known Spanish networks whose feeds contain conflicting language tags.
Unknown or multilingual channels remain visible. Exclusions apply only to country
browsing; search and Favorites retain the complete library. Regenerate with:

```sh
python3 tools/update-lineup-language-filter.py --country US --language spa \
  --overrides tools/lineups/us-language-overrides.json --retain-category sports
```

The sanitized Xfinity Chicago snapshot remains in `tools/lineups` as reference
data for the generic numbered-lineup importer; it is not the active US policy.

UK uses the current TV sections of Wikipedia's DTT list with the main England
numbering and first network variant at shared slots (ITV1 rather than STV).
Regional-only entries remain available after the ranked lineup. Metadata's `UK`
country code is reconciled to the app's `GB` code. HD/SD variants use the lowest
matched number; `+1` timeshifts remain distinct. Radio and historical tables are
not imported. Capture and rebuild the GB rules with:

```sh
python3 tools/update-country-order.py --country GB \
  --url https://en.wikipedia.org/wiki/List_of_DTT_channels_in_the_United_Kingdom \
  --capture tools/lineups/freeview-england.json --region Eng --primary-variant \
  --section 'General Entertainment' --section High-definition --section "Children's" \
  --section News --section 'Streamed channels' --section Interactive --section Adult
python3 tools/import-numbered-lineup.py --country GB \
  --lineup tools/lineups/freeview-england.json \
  --matches tools/lineups/freeview-england-matches.json
make check
```

## Channel keys during playback

MainActivity consumes Android `KEYCODE_CHANNEL_UP` and `KEYCODE_CHANNEL_DOWN`
while a player is active. Channel + moves forward and Channel − backward through
the current country list, Favorites order or search results. Playback from the
France guide uses the France lineup. The sequence is resolved from the effective
catalog and saved browsing context, including after recreation. It wraps, skips
missing Favorites and avoids restarting the only available channel. Held-key
repeat events and key-up events do not cause extra switches.

The previous player is released and its view removed before a new one is attached.
Back restores browsing focus to the newly selected channel. `ChannelNavigationTest`
covers ordering and boundaries; native `ChannelKeysTest` checks remote events,
wrapping, repeat suppression, unavailable Favorites, search, recreation, one
attached player and Back focus. `GuideTest` checks the same keys from the guide.

## Country source overrides

A country's entry in `channel-order.json` can include `sourceOverrides`, keyed by
stable provider ID. Each value is a nonempty list of `{ "label", "url" }` entries,
with optional `headers` for User-Agent, Referer or Origin. HTTP(S) URLs are required;
embedded user credentials and header line breaks are rejected. The current NBC
entry replaces the playlist's alternatives with WMAQ Chicago and KNTV Bay Area,
in that order. Both sources declare 1080p, so their configured order is retained.

`CatalogIndex` applies these replacements before quality preference to every
loaded catalog, including old prepared caches. Names, country, genres, artwork
and favorite IDs remain unchanged. Missing channels are not invented. Refresh
still fetches and retains the fixed worldwide M3U and metadata; no extra startup
download or M3U reparse is introduced. Country browsing, search, Favorites and the
guide all use the effective sources. Alternatives remain explicit viewer choices.

## Stream preference

`StreamOrder` sorts a channel's alternatives by declared resolution, highest first.
Numeric labels such as 1080p/720p and UHD/FHD/HD/SD tokens are recognized. The M3U
variant suffix is used when the display label has no quality; its derived height
is retained in prepared catalogs. Equal-quality alternatives preserve playlist
order. Unknown quality follows declared quality. The order reflects source labels,
not a runtime bandwidth or availability probe.

The parser sorts new catalogs; `CatalogIndex` also applies the policy to existing
prepared caches using their saved labels, without reparsing or downloading the
M3U. Already ordered channel instances are retained. Playback and the Source menu
use the same list; choosing SD stays selected during playback and recreation.

`StreamPreferenceTest` checks the native Source menu, selecting SD and retaining
that choice when the player screen is recreated. Validation on November 3, 2026:
`make check` passed 70 Kotlin unit tests, 12 importer tests, lint and the debug
build. The dedicated `IPTV_Lineup_Check_API_31` emulator passed nine channel-key,
browsing, performance, search, source-preference and guide tests. A separate 30-second NBC
probe using the configured WMAQ override rendered video and audio, started in
3,483 ms, reported zero player errors and zero dropped frames, and kept advancing.
`StreamStabilityTest` resolves country source overrides before selecting its probe
channel, matching the app's effective catalog.

## Channel artwork

The M3U parser preserves the first valid HTTP(S) `tvg-logo` URL for each channel.
Coil loads PNG, JPEG and SVG artwork into portrait cards, with bounded memory and
disk caches and request timeouts. Logo requests identify IPTV in the User-Agent
header. Missing or failed artwork uses a local television placeholder. Logos and
names remain independent of channel order.

## Phone search

The local HTTP listener starts when search is opened, stops when the activity is
hidden, and uses ports
8090–8099 so it can coexist with Movies' 8070–8079 range. It serves the adapted
Movies phone page and IPTV icon. A phone must reach the TV on the same LAN.
Emulator addresses generally require port forwarding for a physical phone;
the native acceptance test exercises the decoded endpoint on the emulator itself.

## Artwork

Run `tools/render-branding.sh` with `rsvg-convert` installed. It uses the bundled
Nunito font and original IPTV SVG to produce launcher densities and the TV banner.
Lato and Nunito license files are included. Country flags are from flag-icons v7.6.0 (MIT), rendered at 160 × 120 and
bundled locally. `python3 tools/update-flags.py` regenerates the pinned SVG/PNG
assets and native resource map. The in-app television artwork is the
same asset used by Sports LiveTV. Neither sibling is modified or required to build.

## France TV guide

The first France tile loads `https://xmltvfr.fr/xmltv/xmltv_tnt.xml` on demand.
Core parses bounded XMLTV with external entities disabled and explicit legacy-ID
aliases. Only channels matched to France's catalog enter the grid, in lineup order.
The app atomically caches validated XML for an hour and retains the last valid
schedule after refresh failures. Refresh in the guide reloads only its schedule.
All program selections play the current live stream, including future listings.

`TvGuideTest` covers offsets, entities, malformed programs and identity matching.
`GuideStoreTest` checks cache reuse and failed-refresh recovery. Run `GuideTest` on
the selected TV emulator for the first tile, remote movement, recreation, program
selection and Back/focus restoration. It supplies a generated schedule through the
real on-device cache; it does not assert live stream availability.

```sh
adb -s emulator-5554 shell am instrument -w \
  -e class fr.bonamy.iptv.GuideTest \
  fr.bonamy.iptv.test/androidx.test.runner.AndroidJUnitRunner
# Explicit live XMLTV fetch and native grid acceptance:
adb -s emulator-5554 shell am instrument -w \
  -e class fr.bonamy.iptv.LiveGuideTest \
  fr.bonamy.iptv.test/androidx.test.runner.AndroidJUnitRunner
```

`LiveGuideTest` requires a fresh feed and captures the rendered native grid to
private `files/live-guide.png` on the selected emulator.

## France guide overlay

During French-channel playback, `KEYCODE_LAST_CHANNEL` (JUMP) toggles a full-screen
translucent guide. `KEYCODE_GUIDE` uses the same action if the TV delivers it to
the app. The native `GuideScreen` has page and overlay presentation modes; both
share the same grid and `loadGuideInto` pipeline backed by `GuideStore`. The
overlay never pauses or releases the active player. Player controls and their
focus are covered while the guide is visible, including during decoder recovery.
Back dismisses the overlay first. Selecting another program tunes its live
channel; selecting the current channel closes the guide without restarting it.
Overlay position, selection and playback are restored on activity recreation.

Run `GuideOverlayTest` separately on the explicitly selected TV emulator. It
backs up and restores the local catalog, guide and preferences for the whole
class, supplies a three-channel prepared catalog and reuses
`ShortLiveWindowTest.Origin` for original synthetic H.264/AAC playback. It checks
advancing video while the guide is open, remote focus, JUMP/Back dismissal, live
selection, recreation and the French-channel boundary. The icon is the supplied
512px transparent PNG, displayed on white; normal channel art retains its own
neutral background.

```sh
adb -s emulator-5556 shell am instrument -w \
  -e class fr.bonamy.iptv.GuideOverlayTest \
  fr.bonamy.iptv.test/androidx.test.runner.AndroidJUnitRunner
```
