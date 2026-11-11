# Ranneki Visualizer Studio — Android V1.6 development

Native Java Android visualizer. Offline rendering and analysis on the device (no server, Termux, internet, or API required at runtime). Target phone Poco F6. This is an **experimental build, not yet field-tested on a device**.

## V1.6 functionality

- Android SAF audio import (MP3/WAV/AAC if Android platform codecs support it), background image import, MediaPlayer playback, offline 32-band FFT analysis, approximated kick/snare/bass/mid/treble, per-layer custom Hz ranges.
- **Dynamic FX layer editor**: up to 48 independent instances from ten built-in FX types (dust, fire, rain, water ripples, smoke, snow, sparks, lightning, glow, glitch). Add/duplicate/delete, enable, order, and customize each independently. Independent particles/ripple state, beat source, density, size, strength, velocity, direction, alpha, threshold, attack/release, blend/mask, per-property routes, and animated strength keyframes.
- **Waveform timeline overview**: peak-pooled audio dynamics, scrubbing by touch, highlighted current playhead, a focusable four-lane view of the FX layer time ranges, and visible strength-keyframe markers. Timeline can be collapsed to preserve preview height on phones. This is a timeline *overview*, not a full multitrack waveform editing interface; live per-layer volume/track splitting is not implemented.
- Project JSON serialized into local SharedPreferences as a single preset (including layer order / independent settings). Does not yet include image source assets; import background manually if the app relaunches.
- **Export MP4 offline with audio** via Android `MediaCodec` H.264 Surface encoder + OpenGL ES texture upload from the deterministic frame-by-frame Canvas renderer, and Android `MediaMuxer` output. AAC inputs are re-muxed if compatible; MP3/WAV/non-AAC are decoded and transcoded to AAC-LC, then muxed. Default 720p30; also 540p30 and experimental 1080p30. Save with Android `ACTION_CREATE_DOCUMENT`, progress and tap-to-cancel. Export may consume significant CPU/GPU, memory, storage, and battery.
- H.264 encoder export now reports an error if EOS is not returned (instead of treating a timed-out output as a successful file). Audio/video sample-copy checks reject empty tracks. Encoder and muxer finalization has additional guards.
- Export uses a copied scene state independent of live preview; visuals advance through analysed timestamps in increasing frame order; resulting video timestamp is frame-index-based (30 fps), and audio timestamps are copied or normalized from source. Export expected to support common Android codec implementations, but not verified on a physical Poco F6.

## Important limitations

- **No field test on a real Android phone yet**. CI compile/test success does not guarantee hardware codec and EGL behavior. Need test with AAC, MP3, WAV and various sample rates (including audio silence / skipped PTS).
- Layer editor supports built-in FX, static image, and text. Full multitrack timeline editing, video clip layers, arbitrary GLSL shaders and freeform masks are not implemented. Current waveform displays a peak-envelope derived from audio analysis; it is not exact sample-level PCM waveform. Strength keyframes exist, other properties use manual values/audio routing; broader keyframing not yet complete.
- Export supports fixed aspect-ratio resolutions and 30 FPS. It's not a GPU-only pipeline: CPU Canvas draws each frame to Bitmap before uploading to the GPU encoder surface, potentially generating heat. File size and temporary storage usage can be large; 1080p is experimental.
- Input formats ultimately depend on Android platform codec support. >2-channel non-AAC sources currently not supported; PCM conversions limited to standard PCM16/8/24/32 and float. A failed export may leave an empty SAF document; temporary cache files are cleaned.
- No audio mastering; AAC transcode uses ~192 kbps. Audio sync and visual orientation require device validation before release.

## Build Android debug APK

Open [GitHub Actions](../../actions), select **Build Android APK**, download `RannekiVisualizerStudio-debug-apk` artifact from a successful run. Uses JDK17, Android SDK35, Gradle8.9 and unit tests for FFT/envelopes/strength keyframes.

## Tests

```bash
mkdir -p build/pure-tests
javac -d build/pure-tests \
  app/src/main/java/com/ranneki/visualizer/{Fft,BandSpectrum,ReactiveEnvelope,AutomationTrack,FrameTimebase,TimelineMath}.java \
  tests/{SmokeTest,AudioReactiveTest,AutomationTrackTest,FrameTimebaseTest,TimelineMathTest}.java
for test in SmokeTest AudioReactiveTest AutomationTrackTest FrameTimebaseTest TimelineMathTest; do java -cp build/pure-tests com.ranneki.visualizer.$test; done
```

## Follow-up priorities

1. Test audio sync, correct video orientation, cancel/error handling and Android hardware encoder availability on Poco F6.
2. Efficient GPU offscreen compositing to reduce CPU Bitmap/texture upload burden.
3. Video clip layers, drag-and-drop scene lanes, expanded parameter keyframes, sample-level waveform rendering, freeform masks, JSON file import/export, 60 FPS and hardware render validation.

## V1.6 status (not a final release)

- `TimelineView` draws a live waveform overview, lane trim blocks and keyframe ticks; the app avoids heavy timeline redraw when collapsed.
- `TimelineMathTest` adds bounded timeline geometry checks.
- Build on GitHub Actions validates compilation and pure-Java tests, **not** H.264/AAC runtime operation on the Poco F6.
- No new install is required until the user chooses to test a release candidate.
