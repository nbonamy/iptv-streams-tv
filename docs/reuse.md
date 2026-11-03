# Sibling reuse assessment

Source inspection: November 3, 2026. Both siblings were read without modification.

| Area | Existing evidence | Implication for IPTV |
| --- | --- | --- |
| Movies browsing shell | `movies-stream/app/src/main/java/fr/bonamy/movies/MainActivity.kt`, `MediaCardAdapter.kt`, `SearchResultsView.kt`, `SiteMenuDialog.kt` | Native Views, RecyclerView/Leanback search, focus restoration and existing navigation are plausible foundations. Poster/details and site-specific flows need adaptation for live channels. |
| Movies playback | `MainActivity.kt`, `PlaybackProgress.kt`, `NextEpisodePrompt.kt` | Media3, subtitle/quality controls and layered Back handling exist. Resume and episode logic are VOD concerns, not prerequisites for live TV. The Activity combines many responsibilities; copying it wholesale would bring unnecessary coupling. |
| Sports live browsing | `sports-stream-tv/android/app/src/main/java/fr/bonamy/sports/LiveTvView.kt`, `TvUi.kt` | Country/channel grids, selected-item focus restoration, Lato typography, dark surfaces and visible blue focus already match a live-TV use case. |
| Sports playback/recovery | `LivePlayback.kt`, `PlayerChrome.kt`, `RetainedVideoFrame.kt` in the same directory | Candidate patterns for live selection, reconnecting and player controls. Provider-specific discovery and source handling need separation from generic M3U ingestion. |
| Playback dependencies | Both apps' `app/build.gradle.kts` (Sports under `android/`) | Both declare Media3 1.11.1 for ExoPlayer, HLS and UI. This is a checked sibling baseline, not a dependency decision for the new project. |
| Visual identity | Movies' `branding/movies-icon.svg`, both `branding/*-banner.svg`, Movies' `docs/mediastation-ui.md`, Sports' `docs/artwork/README.md` | Reuse the blue/cream/gold launcher family and the restrained dark, remote-first interface conventions. |

Paths in the table are relative to `/Users/nbonamy/src/`.

## Selected integration

IPTV uses a small native Views shell with an independent M3U/catalog core. Sports
LiveTV is the visual reference: five-column country cards, compact header,
TV artwork, and focus treatment. IPTV's six-column portrait channel cards show
source logos above names. The transport
button and loading view are copied from Sports. Movies supplies the Leanback
search/voice/QR flow, phone HTTP endpoint and page, Lato fonts, search assets, and
player underline selectors. The original IPTV launcher mark remains distinct.

Substantial source transfers were compared byte-for-byte before changing package
names and the necessary IPTV boundaries. The phone endpoint uses ports 8090–8099
rather than Movies' 8070–8079. No VOD/provider extraction code or shared framework
is carried over. Both siblings remain independent and unchanged.

The IPTV core owns metadata joins, stable channel IDs, playlist variants, search,
country/category filtering and bounded source requests. Android owns atomic cache
replacement, favorite ordering/corrections, activity visibility, remote focus and
Media3 lifecycle. Tests and emulator acceptance are described in
[development](development.md).

## Research boundary

The Electron IPTV report is prior static evidence, not a runtime specification or
a requirement to reproduce its stack. Compose, Room, WorkManager, Xtream and XMLTV
in that APK do not decide this project's architecture or first-release scope.
Current IPTV validation is tracked in [development](development.md); the APK
research does not establish parity with Electron IPTV.
