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
favorites, logo parsing, country lineup ordering, metadata corrections, offline startup and failed-cache replacement.
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

## Source and storage

Built-in source: `https://iptv-org.github.io/iptv/index.m3u`.
Metadata: `https://iptv-org.github.io/api/channels.json`.
The bundled snapshots under `app/src/main/assets/` were retrieved November 3, 2026.
No user playlist/account entry is provided. A successful refresh validates before
replacing the local cache through Android AtomicFile. A failed refresh leaves the
working catalog intact. Invalid caches fall back to the bundled source.

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
origin. No DRM, catch-up, EPG, recording, multiview, or source credentials UI is added.

## Country channel order

`app/src/main/assets/channel-order.json` supplies optional lineups keyed by
country code. Each entry contains source provenance and a `channels` array:

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

## Channel artwork

The M3U parser preserves the first valid HTTP(S) `tvg-logo` URL for each channel.
Coil loads PNG, JPEG and SVG artwork into portrait cards, with bounded memory and
disk caches and request timeouts. Logo requests identify IPTV in the User-Agent
header. Missing or failed artwork uses a local television placeholder. Logos and
names remain independent of channel order.

## Phone search

The local HTTP listener runs only while the activity is visible and uses ports
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
