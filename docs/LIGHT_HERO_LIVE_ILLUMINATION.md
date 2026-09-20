# Light hero: effective-output illumination

## Scope and invariant

The existing `device_light_hero_card.webp` is unchanged. Card dimensions, aspect ratio,
FillBounds artwork mapping, clipping, outline, and W/K typography/positions are unchanged.
Only artwork illumination is dynamic. Metrics are drawn afterwards, outside the lighting
passes. At full WRGB output, the renderer draws the source painter directly with no filter
or overlay; an instrumented pixel comparison protects this invariant.

## Authoritative input

`DeviceLightDashboardScreen` passes the same `state.channels` to the hero and to the live
output card. The hero consumes `DeviceLightChannelOutputSnapshot.effectivePercent`, keyed
by the Light V1 `red`, `green`, `blue`, and `white` wire identities. It never uses requested
slider values, estimated watts/Kelvin, a local clock, or the graph as a substitute for
physical output. Manual, automatic, custom, preview, acclimation, and safety-derated output
therefore follow the same validated effective-output stream.

An explicit inactive or unknown output renders dark. An active frame containing zeros also
renders dark. Missing white on an RGB product stays absent. Channel order and translated
names do not affect mapping. Out-of-range integer input is clamped defensively; the existing
runtime validation remains untouched.

The existing ViewModel retains the last complete validated frame during a transient read
failure. This change preserves that contract: without new telemetry the image holds its
last confirmed state and does not invent a continuing sunrise. The existing connection
indicator remains the freshness signal. The hero is keyed by device UID so animation state
cannot leak from another device.

## Rendering and animation

- Four effective levels are interpolated together with a single `AnimationVector4D` and a
  finite 320 ms linear tween. Updates interrupt from the current displayed levels. There is
  no autonomous timer, spring overshoot, infinite animation, or additional device request.
  Compose's animation duration scale is respected. First composition starts at the current
  target, not at the fully lit source image.
- RGB and white contributions are mixed before an sRGB-shaped display response. The
  denominator uses installed channels, not active channels, so a low-output sunrise cannot
  normalize itself to full brightness. Off-state ambient is a small neutral luminance term,
  preserving the tank silhouette without retaining colorful light emission.
- The diffuse pass uses a color matrix on the original texture. A second, small lamp-region
  pass independently attenuates the source image's baked-in red/green/blue/white hotspots.
  A dim neutral reflection layer preserves the surface under inactive emitters instead of
  punching black holes into a scene illuminated by another channel. Feathered masks are
  registered in normalized artwork coordinates. An off green channel,
  for example, cannot leave the original green hotspot at full brightness merely because
  white is on.
- Only lamp-sized intermediate surfaces are composited. The same resource painter is
  reused; there are no per-frame bitmap decodes, bitmap copies, generated assets, or extra
  libraries. Geometry and feather brushes are cached. Animated state is read in draw rather
  than layout/composition. The implementation uses Canvas/ColorMatrix/Porter-Duff operations,
  not API-33-only shaders, keeping the application's API 27 minimum intact.

This is a telemetry-driven relighting of a static illustration, not calibrated photometry.
PWM percentage, the fixture's spectral power distribution, scene depth/materials, camera
exposure, and display response are not interchangeable. Visual progression follows received
effective percentages, with the bounded tween delay; it cannot assert measured lux/PAR,
exact real-world colors, or knowledge of physical changes before telemetry arrives.

## Verification

`DeviceLightHeroLightingTest` and `HeroSceneExposureTest` cover input mapping, cold/off state,
zero output, reordered channels, RGB/white-only profiles, clamping, pure-color output,
installed-channel normalization, exact full-output identity, and monotonic low-light ramps.

`HeroAquariumRendererInstrumentedTest` draws the actual resource through the production
renderer to check full-output pixel identity, extinguished baked-in hotspots, independent
emitters, progressive brightness with fixed dimensions, and painter reuse without ghosting.

Run on the repository's configured Android build environment:

```sh
./gradlew :app:testDebugUnitTest \
  --tests '*DeviceLightHeroLightingTest' --tests '*HeroSceneExposureTest'
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root.HeroAquariumRendererInstrumentedTest
```

Also review on a device with live output at 0/1/10/50/100 percent, each isolated channel,
manual changes, sunrise/sunset, reconnect, rotation, and a switch between devices. No lint
baseline, suppression, dependency lock, dependency verification metadata, or workflow is
modified by this feature.
