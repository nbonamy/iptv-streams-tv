# IPTV product brief

<!-- impeccable:product-schema 1 -->

## Platform

android

Native Android TV, operated from a television remote. English is the default
interface language. IPTV belongs to the TV team alongside Movies Stream and Sports.

## Users and purpose

Nicolas wants a simple way to find and watch live television from an M3U playlist
chosen for the app and embedded in its configuration. Success means reaching a wanted channel quickly from the sofa, with
predictable focus, fullscreen playback, and easy return to browsing.

## Intended capabilities

- Live TV only for the first version.
- Embed the iptv-org worldwide M3U: `https://iptv-org.github.io/iptv/index.m3u`.
  Users open the app directly into channel browsing;
  there is no M3U URL/file picker, playlist onboarding, or source-management UI.
- Give **France** and **USA** direct, first-class browsing access regardless of
  whether the chosen playlist is regional or worldwide. These are country views,
  not language-only filters: French-language content is not necessarily from France.
- Provide user-curated **Favorites**, with explicit add/remove and move-up/move-down
  ordering. Favorites survive ordinary playlist refreshes.
- Keep all other countries available, with global search across the full library. Country priorities affect
  presentation, never remove channels from the library.
- Provide channel search, clear channel selection, and native fullscreen playback.
- Keep other interests configurable. Do not infer teams, leagues, genres, political
  interests, language exclusions, or paid-provider preferences from “you know my taste.”

## Browsing direction

The confirmed home order is **Favorites → France → USA → UK → Canada**.
These are the first five tiles on the home grid; there is no separate navigation
row. All other countries follow in the same grid. Worldwide browsing uses Sports’
five-column tile layout and portrait channel cards with the source logo above the name. Favorites uses a yellow
star, and countries use locally bundled flag-icons artwork. Search is a secondary action and includes the shared QR-assisted phone keyboard.
Search shows matches only after a query is entered; it never presents the whole
library as initial results. The large TV/title hero appears only on home; channel
lists reclaim that space and use the header breadcrumb for context. Show country
flags alongside matches. Preserve the focused channel and browsing position on
return from playback.

Countries can supply a numbered channel lineup. France follows the metropolitan
national DTT lineup in the [French television station list](https://en.wikipedia.org/wiki/List_of_television_stations_in_France).
Listed channels appear first in broadcast-number order; other channels remain
available alphabetically afterward. Only channels present in the selected source
appear. Other countries and global search retain alphabetical order until a
country lineup is supplied. Favorites always retain the viewer's manual order.

M3U metadata may be incomplete or inconsistent. Preserve provider names and groups;
use explicit country/category metadata where available.
Keep unknown-country and uncategorized channels accessible. Missing metadata must
not silently exclude a channel from global search. Do not fabricate a programme
guide or sports schedule when the source does not supply one.

## Product principles

1. A remote is sufficient: D-pad, Select, and physical Back cover the main flow.
2. Short labels, visible focus, readable channel names, and minimal on-screen copy.
3. France and USA first; the rest of the world remains within reach.
4. Personalization comes from explicit choices and favorites.
5. Open directly into the embedded channel library; browsing priorities stay
   independent of the chosen playlist’s geographic scope.

## Brand commitments

Use the name **IPTV** and a simple original television mark. Follow the siblings'
launcher identity and remote-first product conventions; see [branding](branding/README.md).

## Implementation direction

The first version is a native Views-based Android TV app with Media3. Sports is
its live-TV visual reference: compact header, TV artwork, five-column
home tiles, Lato typography, and blue remote focus. Six-column portrait channel
cards show source logos, with a television placeholder when artwork is unavailable.
Movies provides the Leanback/voice/QR-assisted search flow and underline player
menus. Shared resources are copied locally so neither sibling is a build dependency.
Playback prefers hardware decoding and includes FFmpeg fallback for unsupported
audio and failing device decoders, keeping viewing inside the app.

A prepared source snapshot is bundled for immediate startup without a network
connection. Startup and browsing use the saved local catalog. Refresh explicitly
downloads the fixed worldwide source, validates it, and replaces the persistent
cache atomically. Country/category metadata joins iptv-org channel IDs.

Movies and series are outside the first version. XMLTV/now-next, catch-up,
recording, multiview, time-shifting, and background sync
remain possible later decisions, not committed initial scope. User-supplied
playlist/account setup is outside the product direction. Validate playback before making
stream availability or content claims.

The selected source is the [iptv-org worldwide playlist](https://iptv-org.github.io/iptv/index.m3u),
from [iptv-org/iptv](https://github.com/iptv-org/iptv). Its country and sports lists
are organizational references, not separate selected sources. The app owns this
source choice; users do not configure it.

On November 3, 2026, GitHub reported 143,125 stars for iptv-org/iptv versus 21,688
for Free-TV/IPTV, supporting the choice by public repository popularity. The
worldwide playlist returned HTTP 200, a valid EXTM3U header, and 11,403 EXTINF
entries. These are stream entries, not verified unique or playable channels.
Individual channel availability varies. Playback acceptance evidence belongs in
[development](docs/development.md); it does not establish availability of the whole library.

## Evidence on hand

- Current sibling source and artwork inspected on November 3, 2026; paths and
  findings are recorded in [the reuse assessment](docs/reuse.md).
- Prior Electron IPTV static inspection:
  `/Users/nbonamy/Downloads/electron-iptv-analysis-2026-11-03/analysis.md`.
  Downloaded APK 3.1.0 differed from website 3.2.0; no runtime test was performed.
  Its user-supplied-source model is research context, not this app’s product
  model. Its findings do not establish channel availability for the chosen M3U.

## Validation boundary

Validate parser/catalog behavior with fixtures, persistence and offline cache
recovery through owning-boundary tests, and remote browsing/search/playback on an
explicit Android TV emulator. Phone search must deliver a decoded QR endpoint query
and stop listening when the activity is no longer visible. Physical-TV installation,
Git commits, publication, and deployment require a separate instruction.
