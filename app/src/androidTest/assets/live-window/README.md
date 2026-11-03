Original synthetic H.264/AAC test media, generated with FFmpeg's testsrc2 and sine
sources. No provider media, URLs or credentials are included.

```sh
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i 'testsrc2=size=160x90:rate=10' \
  -f lavfi -i 'sine=frequency=440:sample_rate=24000' -t 70 \
  -c:v libx264 -preset ultrafast -b:v 50k -maxrate 50k -bufsize 100k \
  -g 20 -keyint_min 20 -sc_threshold 0 -c:a aac -b:a 24k \
  -hls_time 2 -hls_list_size 0 -hls_segment_filename '%02d.ts' \
  -f hls /tmp/fixture.m3u8
```

The native test serves four segments in an eight-second sliding window, with a
three-second target duration. It delays the first segment response long enough
for the oldest segment to expire. This exercises real Media3 startup and recovery
without depending on a public stream or network conditions.
