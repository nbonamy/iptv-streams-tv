# Working on IPTV

Read PRODUCT.md and DESIGN.md first. Keep the built-in iptv-org worldwide source,
Favorites / France / USA / UK / Canada tile order, and full worldwide access.
There is no user playlist setup.

- Reuse the native Movies/Sports visual language and resources. Country browsing
  uses Sports' grid geometry with local country flags; channels use six-column
  portrait cards with a source logo above the name. Search
  includes the shared Leanback, voice and QR-assisted phone flow.
- The large TV/title hero appears only on home; country lists, Favorites and search
  use the compact header and breadcrumb.
- Country channel order is configured in `app/src/main/assets/channel-order.json`;
  preserve manual Favorites order and alphabetical fallback for unlisted channels.
- Keep parser/catalog behavior in core, lifecycle/persistence/UI in app. Use stable
  provider IDs for favorites and explicit choices for alternate streams.
- Run `make check`; for UI/search/playback changes also run the relevant native
  tests in docs/development.md on an explicitly selected Android TV emulator.
- Preserve sibling repositories. No shared cross-repository dependency is needed.
- No physical-TV installation, Git commits, branches, pushes, or deployment without
  a separate instruction. Keep local SDK paths and generated build output ignored.
- Keep credentials and transient private playback data out of logs and fixtures.
