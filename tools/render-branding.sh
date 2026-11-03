#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
font_config=$(mktemp)
trap 'rm -f "$font_config"' EXIT HUP INT TERM
cat > "$font_config" <<EOF
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig><dir>$root/branding/fonts</dir></fontconfig>
EOF
export FONTCONFIG_FILE="$font_config"
for item in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
    density=${item%:*}
    size=${item#*:}
    mkdir -p "$root/app/src/main/res/mipmap-$density"
    rsvg-convert --width "$size" --height "$size" "$root/branding/iptv-icon.svg" -o "$root/app/src/main/res/mipmap-$density/ic_launcher.png"
done
rsvg-convert --width 320 --height 180 "$root/branding/iptv-banner.svg" -o "$root/app/src/main/res/drawable-xhdpi/tv_banner.png"
