# Light hero: effective WRGB relighting

## Contract

The existing `device_light_hero_card.webp`, card surface, aspect ratio, FillBounds
mapping, metric bounds and typography are unchanged. Only the artwork's draw
pass is affected. Power and CCT text are drawn afterwards, outside the filter.
At R=G=B=W=100 the renderer bypasses every filter and layer: the original image
is drawn directly. At zero output a neutral 4% display-value floor preserves a
faint silhouette without bright emitters. This floor is ambient artwork detail,
not a nonzero device-output reading.

## Authority and time

`DeviceLightRootUiState.channels` is the same complete validated snapshot used by
the live output bars. The data adapter maps `light.status.get` effective percent
fields into these channels. The hero resolves `red`, `green`, `blue`, `white` by
wire key, never list order, translated labels, requested settings, watts, CCT or
local wall-clock time. An explicit off/unknown output renders dark. A missing
channel is zero; duplicate keys are ambiguous and do not light that emitter.
Power-limited but active output uses the reported effective levels, without
applying the reduction again.

One four-dimensional animation retargets received levels together over 350 ms
with no overshoot. It starts at the first sample, not at an invented bright or
dark sample. Explicit device-off is applied immediately in the drawing path,
even if a previous transition has not completed. Device identity keys the hero
composition so state cannot animate across devices. There is no timer, network
poller or autonomous day-cycle in the renderer. If the existing runtime retains
a last complete frame during refresh/disconnection, the artwork retains that
frame too; it does not claim to know unreported physical changes.

This visualization follows device telemetry cadence plus the short presentation
transition. It does not predict the intervals between telemetry samples. It is
not a calibrated optical simulation: one already-lit illustration cannot encode
real spectral reflectance, tank geometry or measured fixture photometry. The
exposure response is sRGB-shaped; equal white/RGB contributions are an artwork
normalization, not a watt/PAR claim. CCT calibration is not required for this UI.

## Rendering and compatibility

Scene RGB exposure is mixed from effective R/G/B plus neutral W. Four soft,
normalized elliptical masks replace the existing emitter/glow regions with
channel-specific exposure. Replacement, rather than additive colored circles,
ensures a disabled white/red/green/blue emitter can become dark while other
channels light the tank. No objects, image crop or new lighting geometry are
introduced.

The Canvas implementation uses color matrices, clipped saveLayer and DST_IN,
available on the app's entire API 27+ range. It does not require Android 13
RuntimeShader, a library update or a different fallback image. Masks, bounds
and paints are cached per drawing size; animation state is read only during
drawing. No bitmap is allocated, decoded, copied or uploaded by the relighter
on each frame. Uniform WRGB levels skip all four local passes. Full output
skips the renderer entirely.

## Verification

JVM tests cover reported channel mapping, mode/calibration independence,
missing/duplicate/off data, clamping, power limitation, 10,000-step monotonic
exposure, RGB/white response and non-overlapping emitter centers. Android
instrumentation tests exercise the production Canvas renderer for full-output
pixel identity, off-state brightness and alpha, independent emitter response,
scene color, non-accumulation and balanced canvas saves.

On a physical fixture, verify manual off/on, isolated R/G/B/W, automatic/custom
sunrise and sunset, power limitation, quick retargeting, navigation between
devices and disconnect/reconnect. Confirm the hero agrees with the live output
bars and that W/K text, card corners and image framing never move or dim.
