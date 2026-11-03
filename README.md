# IPTV

Native Android TV app for live channels from the built-in
[iptv-org worldwide playlist](https://iptv-org.github.io/iptv/index.m3u).

- Home tiles: **Favorites → France → USA → UK → Canada**, then other countries.
- Worldwide country browsing, with France and USA first.
- Portrait channel cards with logos above names and a clean fallback for missing artwork.
- Country-specific channel lineups: France follows national DTT numbering; remaining channels follow alphabetically.
- Channel search from the TV, voice input where available, or a phone via QR code.
- Native fullscreen playback, quality/audio/subtitle selection, and explicit source selection.
- Saved favorites, manual ordering, and country/sports corrections.
- Bundled channels for offline startup, automatic refresh, and a persistent cache.

The interface follows the TV family's Lato typography, dark surfaces, blue focus,
Sports country grid, and Movies playback menus and phone search.

## Screenshots

| Countries | Live playback |
| --- | --- |
| ![Country browsing](docs/screenshots/countries.png) | ![Live player](docs/screenshots/player.png) |

![France channel cards](docs/screenshots/channels.png)

## Usage

Select a channel to watch. Hold **Select** on a channel for favorites, ordering,
and category options. Use the home tiles for countries and collections;
Search covers the worldwide library. **Back** returns to the selected tile.

France uses the [metropolitan national DTT lineup](https://en.wikipedia.org/wiki/List_of_television_stations_in_France).
Other countries use alphabetical order unless a country lineup is configured.
Favorites always keep your manual order.

Choose **Search** to type on the TV, use the microphone, or scan the QR code with
a phone on the same network. Enter a channel name to see matches, with country
flags for context. The search screen stays empty until you enter a query.

During playback, use the top menus for subtitles, quality, audio, source, and
favorites. The bottom control pauses/resumes. **Back** hides controls, then returns
to the same channel in browsing. Streams are selected explicitly; reconnecting
retries the current stream.

Focus the **IPTV / section** header and press **OK** to open the menu.
**Refresh** reloads the built-in playlist and channel metadata.

## Build

Requires JDK 17 and Android SDK 36. Runs on Android TV 8.0 or later.

```sh
make check
make build
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Installation always needs an explicitly selected target:

```sh
make install ANDROID_DEVICE=emulator-5554
make run ANDROID_DEVICE=emulator-5554
```

See [development](docs/development.md), [product direction](PRODUCT.md),
[visual conventions](DESIGN.md), and [third-party notices](THIRD_PARTY_NOTICES.md).
