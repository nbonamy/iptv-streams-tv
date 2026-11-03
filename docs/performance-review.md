# Performance review

Measured November 3, 2026 on the Sony BRAVIA 4K VH2 (Android 12) and the explicitly
selected Android TV API 31 emulator. The review covers app/core startup, catalog
parsing and storage, filtering, preferences, adapters, artwork, refresh, phone
search, and playback resource/lifecycle boundaries.

## Findings and changes

- **Repeated source parsing:** a versioned prepared channel snapshot is retained
  alongside the original M3U and metadata. Startup reads the channel projection;
  source strings are loaded only when needed. The application reuses its snapshot
  across activity recreation.
- **Downloads on launch/resume:** catalog downloads occur only through the
  explicit header Refresh action. Browsing always uses local data.
- **Full-catalog work on country selection:** country lists, broadcast order,
  channel-ID lookups and folded search strings are prepared once. Favorites look
  up their saved IDs directly; country/sports override scans are removed.
- **Expensive sort keys:** channel normalization and localized country names are
  computed once per item rather than for every sort comparison. ASCII search
  avoids unnecessary Unicode normalization.
- **First-install disk stalls:** the build prepares the bundled snapshot using
  the runtime parser. Channels are available before the atomic seed write; a
  completed Refresh supersedes any pending seed.
- **First-grid and repeat rendering cost:** artwork infrastructure is warmed off
  the UI thread, a bounded pool of reusable cards is prepared one per frame, and
  unchanged adapter data avoids redundant rebinds.
- **Unneeded startup work:** QR assets and the phone listener initialize when
  search is opened. The listener still stops when the activity is hidden.
- **Load/refresh race:** the initial local load cannot overwrite a newer explicit
  Refresh result.

Channel options retain favorite add/remove and manual ordering. The persistent
instruction footer and country/sports editing actions are removed.

## Measurements

| Operation | Sony TV |
| --- | ---: |
| Read saved channel catalog | 558 ms |
| Activity ready with local countries, instrumented process | 1,366 ms |
| Android cold-process launch to first frame | 1,819 ms |
| First France grid draw | 233 ms |
| Subsequent France grid draws | 21 ms |
| First-install catalog available | 513 ms |
| First-install durable cache write, outside UI readiness | 2,787 ms |

The emulator baseline took 2,121 ms reading/parsing its local catalog and 526 ms
to draw France. Instrumentation measures channel names/cards reaching pre-draw;
it does not wait for remote logo downloads or measure stream startup. Activity
readiness is measured inside instrumentation; the separate cold-process launch
includes Android process and framework startup.

## Verification

All 41 unit tests pass. `make check` covers parser/index behavior, versioned cache recovery, old-cache
upgrade, preserved favorites, failed-refresh preservation, concurrent refresh
reads, and pending-seed publication. Debug and release APKs build successfully.
All seven emulator native checks pass. Native checks cover the real grids, favorite-only menus, search, QR delivery and
lifecycle, explicit Refresh completion, playback and return to search. The Sony
performance probe passes both cases, enforcing country draws below 250 ms and separately verifying
catalog readiness and durable persistence.

Playback's existing request/memory bounds, decoder recovery, player release and
hidden-animation cleanup remain in place. Artwork retains bounded memory/disk
caches and network timeouts. No sibling repository is modified.
