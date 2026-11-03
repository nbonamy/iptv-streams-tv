---
name: IPTV
description: Native Android TV live-channel browsing in the Movies and Sports visual family.
colors:
  background: "#07111D"
  panel: "#12171E"
  focus: "#3498DA"
  focused-panel: "#121B26"
  muted: "#8FA3B8"
  text: "#FFFFFF"
  player-text: "#CCCCCC"
  error: "#ED6673"
  favorite: "#F2B632"
typography:
  heading:
    fontFamily: Lato
    fontSize: 30sp
    fontWeight: 700
  channel:
    fontFamily: Lato
    fontSize: 15sp
    fontWeight: 700
  country-code:
    fontFamily: Lato
    fontSize: 26sp
    fontWeight: 700
  country-name:
    fontFamily: Lato
    fontSize: 13sp
    fontWeight: 400
rounded:
  tile: 7dp
spacing:
  screen: 36dp
  grid-gap: 12dp
---

# IPTV design system

## Overview

Follow the actual native TV siblings. Sports LiveTV supplies the compact header,
television artwork, country tiles and focus treatment. Movies supplies the
Leanback search field, QR-assisted phone search, and player menu treatment.
The launcher keeps IPTV's original simple TV mark.

## Colors

Use the Sports dark browser background and panel colors. Blue identifies remote
focus and the play mark. Favorites uses a gold star; country
flags retain their original colors. Loading is blue; failed playback is red.

## Typography

Bundle Lato regular, bold and black, as the siblings do. Use sp for native text.
The compact brand is 17sp bold with .16 letter spacing; breadcrumbs are 12sp. Section headings are 30sp. Player menus are 14sp uppercase.

## Layout

The browser has a 56dp compact header. The IPTV/breadcrumb group takes remote
focus; OK opens the shared Movies-style action sheet containing Refresh. There
is no separate Refresh button in the content area. Its body has 36dp horizontal padding.
Only the home page shows the hero: Sports' television artwork at 132 × 96dp,
with 22dp between it and the LiveTV heading. Channel lists start directly below
the compact header, whose breadcrumb identifies the country or collection.
The first home row contains Favorites, France, USA, UK and Canada tiles. There is no separate navigation row.

Home uses five columns of 94dp tiles: Favorites, France, USA, UK, Canada,
then the other countries. The Favorite star is yellow. Country flags use 4:3
flag-icons artwork above the name, with code text only when no flag is available.
Channels use six columns of 172dp portrait cards, expanding for unavailable status.
A 112dp logo area on a light
neutral surface sits above the centered, two-line channel name. Keep source logos
uncropped; missing artwork uses a muted television placeholder. Cards use 8dp
outer padding and a 4dp gap before the name. Country lineups
control card order where configured; Favorites retain manual order.
Both grids scroll and restore remote focus. Search hides the TV artwork/title
hero and places the search field and QR shortcut above results. Empty or
whitespace-only queries show no channel cards. Matches include a small country
flag for context. No source-setup form is shown.

## Elevation & Depth

Use tonal surfaces and blue focus outlines in browsing. Avoid raised filled
button bars. Playback uses the Movies translucent black top strip and Sports'
bottom gradient, leaving full-screen video visible behind the controls.

## Shapes

Keep the Sports tile radius and 2dp focused border. Header actions use a discreet
translucent focused background. Player menu focus is the Movies cream underline,
not a blue filled button.

## Components

- Home grid: `CountryAdapter.kt`, using Sports tile geometry and flag-icons artwork.
- Channel cards: `ChannelAdapter.kt`, portrait source artwork with the shared dark surfaces and blue focus.
- Search: Leanback SearchBar plus the copied QR/phone-search components from Movies.
- Player: copied Sports transport/loading views and Movies underline selectors;
  Subtitles, Quality, Audio, Source and Favorite menus above the video.
- Physical Back dismisses a picker, hides controls, then returns to the focused channel.

## Do's and Don'ts

- Do reuse sibling assets and real native controls before inventing replacements.
- Do preserve QR/phone-search, D-pad focus, and Back behavior alongside appearance.
- Do keep channel and country labels short and source-derived.
- Don't replace the compact header with an oversized app-name banner.
- Don't turn country browsing into a long modal picker.
- Don't add explanatory subtitles where the layout already provides context.
