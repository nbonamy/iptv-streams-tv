# IPTV branding

`iptv-icon.svg` is the original editable source: a cream television outline with
a gold stand on blue. It contains no text, embedded image, external dependency,
or copied third-party glyph. `iptv-icon.png` is its 512 × 512 render for project
identity and previews. Android launcher images and the TV banner are generated
from the same source through `tools/render-branding.sh`.

## Family conventions

| Role | Value | Sibling source |
| --- | --- | --- |
| Launcher background | `#2856C7` | Movies icon; Movies and Sports banners |
| Launcher mark | `#FFF8E8` | Movies icon; Movies and Sports banners |
| Launcher accent | `#F2B632` | Movies icon; Movies and Sports banners |
| App background reference | `#07111D` | Sports `TvUi.kt`; Movies browser header |
| App action/focus reference | `#3498DA` | Both native apps |
| App typography reference | Lato | Movies UI provenance; Sports native theme |
| Banner typography reference | Nunito Bold | Both launcher banners |

App colors and typography follow the implemented native UI in [DESIGN.md](../DESIGN.md). Keep launcher gold distinct from the apps' blue focus accent.
Reuse clear D-pad focus, short labels, full-screen video and predictable physical
Back behavior. Do not add explanatory subtitles where layout or focus is sufficient.

The mark uses a 512-unit square canvas, a 26-unit cream stroke, a wide screen,
and a compact gold pedestal. Keep its proportions and clear space when scaling.
Do not add flags, sport equipment, channel logos, gradients, or text to the icon.

To regenerate with the locally available librsvg renderer:

```sh
rsvg-convert --width 512 --height 512 branding/iptv-icon.svg -o branding/iptv-icon.png
```
