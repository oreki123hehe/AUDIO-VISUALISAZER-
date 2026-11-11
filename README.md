# Ranneki Visualizer Studio — Android V1.7 development

Native Java Android visualizer. Offline rendering and analysis on the device (no server, Termux, internet, or API required at runtime). Target phone Poco F6. This is an **experimental build, not yet field-tested on a device**.

## V1.7 functionality

- Android SAF audio import (MP3/WAV/AAC if Android platform codecs support it), background image import, MediaPlayer playback, offline 32-band FFT analysis, approximated kick/snare/bass/mid/treble, per-layer custom Hz ranges.
- **Dynamic FX layer editor**: up to 48 independent instances from ten built-in FX types (dust, fire, rain, water ripples, smoke, snow, sparks, lightning, glow, glitch). Add/duplicate/delete, enable, order, and customize each independently. Independent particles/ripple state, beat source, density, size, strength, velocity, direction, alpha, threshold, attack/release, blend/mask, per-property routes, and 16 independent numeric keyframe curves (strength, particle size/count/speed/opacity, x/y, scale, rotation, threshold, attack/release, min/max Hz, mask radius and direction).
- **Waveform timeline overview**: peak-pooled audio dynamics, scrubbing by touch, highlighted current playhead, a focusable four-lane view of the FX layer time ranges, and visible markers for all parameter keyframes. Timeline can be collapsed to preserve preview height on phones. This is a timeline *overview*, not a full multitrack waveform editing interface; live per-layer volume/track splitting is not implemented.
- Project JSON serialized into local SharedPreferences as a single preset (including layer order / independent settings). Does not yet include image source assets; import background manually if the app relaunches.
- **Export MP4 offline with audio** via Android `MediaCodec` H.264 Surface encoder + OpenGL ES texture upload from the deterministic frame-by-frame Canvas renderer, and Android `MediaMuxer` output. AAC inputs are re-muxed if compatible; MP3/WAV/non-AAC are decoded and transcoded to AAC-LC, then muxed. Default 720p30; also 540p30 and experimental 1080p30. Save with Android `ACTION_CREATE_DOCUMENT`, progress and tap-to-cancel. Export may consume significant CPU/GPU, memory, storage, and battery.
- H.264 encoder export now reports an error if EOS is not returned (instead of treating a timed-out output as a successful file). Audio/video sample-copy checks reject empty tracks. Encoder and muxer finalization has additional guards.
- Export uses a copied scene state independent of live preview; visuals advance through analysed timestamps in increasing frame order; resulting video timestamp is frame-index-based (30 fps), and audio timestamps are copied or normalized from source. Export expected to support common Android codec implementations, but not verified on a physical Poco F6.

## Important limitations

- **No field test on a real Android phone yet**. CI compile/test success does not guarantee hardware codec and EGL behavior. Need test with AAC, MP3, WAV and various sample rates (including audio silence / skipped PTS).
- Layer editor supports built-in FX, static image, and text. Full multitrack timeline editing, video clip layers, arbitrary GLSL shaders and freeform masks are not implemented. Current waveform displays a peak-envelope derived from audio analysis; it is not exact sample-level PCM waveform. V1.7 provides 16 numeric keyframe curves; discrete choices (blend, mask shape, audio source, enabled state, layer type, text content) are not keyframable.
- Export supports fixed aspect-ratio resolutions and 30 FPS. It's not a GPU-only pipeline: CPU Canvas draws each frame to Bitmap before uploading to the GPU encoder surface, potentially generating heat. File size and temporary storage usage can be large; 1080p is experimental.
- Input formats ultimately depend on Android platform codec support. >2-channel non-AAC sources currently not supported; PCM conversions limited to standard PCM16/8/24/32 and float. A failed export may leave an empty SAF document; temporary cache files are cleaned.
- No audio mastering; AAC transcode uses ~192 kbps. Audio sync and visual orientation require device validation before release.

## Build Android debug APK

Open [GitHub Actions](../../actions), select **Build Android APK**, download `RannekiVisualizerStudio-debug-apk` artifact from a successful run. Uses JDK17, Android SDK35, Gradle8.9 and unit tests for FFT/envelopes/keyframe parameter bounds/PCM audio frame alignment.

## Tests

```bash
mkdir -p build/pure-tests
javac -d build/pure-tests \
  app/src/main/java/com/ranneki/visualizer/{Fft,BandSpectrum,ReactiveEnvelope,AutomationTrack,AutomationParameters,PcmFrameReader,FrameTimebase,TimelineMath}.java \
  tests/{SmokeTest,AudioReactiveTest,AutomationTrackTest,AutomationParametersTest,PcmFrameReaderTest,FrameTimebaseTest,TimelineMathTest}.java
for test in SmokeTest AudioReactiveTest AutomationTrackTest AutomationParametersTest PcmFrameReaderTest FrameTimebaseTest TimelineMathTest; do java -cp build/pure-tests com.ranneki.visualizer.$test; done
```

## Follow-up priorities

1. Test audio sync, correct video orientation, cancel/error handling and Android hardware encoder availability on Poco F6.
2. Efficient GPU offscreen compositing to reduce CPU Bitmap/texture upload burden.
3. Video clip layers, drag-and-drop scene lanes, sample-level waveform rendering, freeform masks, JSON file import/export, 60 FPS and hardware render validation.

## V1.7 status (not a final release)

- `TimelineView` draws a waveform overview, lane trim blocks and markers for every numeric keyframe; the app avoids heavy timeline redraw when collapsed.
- `AutomationParametersTest` checks parameter-specific limits, independent curves and serialization; `PcmFrameReaderTest` verifies incomplete input reads cannot drop half a sample frame. Existing FFT, envelope, timebase and timeline tests remain.
- Build on GitHub Actions validates compilation and pure-Java tests, **not** H.264/AAC runtime operation on the Poco F6.
- No new install is required until the user chooses to test a release candidate.

## V1.8: GPU post FX, export profiles and real-device performance diagnostics

- Export options: 540p/720p/1080p, 30 fps; experimental 1080p at 60 fps if H.264 codec capabilities allow it.
- Internal Canvas raster can be 65–100% of target resolution and GPU (GLES 2.0) upscales the final encoded frame; at reduced quality the tradeoff is visible softness.
- New GPU fragment shader handles audio-reactive RGB channel separation, vignette and a restrained bass/kick pulse on the final image. **The scene and particles remain Canvas-rendered on CPU**: this is not yet a fully GPU-native particle renderer.
- Export pipeline reports measured CPU render time, EGL GPU upload/swap time, throughput FPS, recent frame-time p95, thermal *severity* (not temperature in Celsius), Java heap usage and final MP4 size.
- The **Performa** and **Benchmark** controls allow collection of actual preview frame timings after installation; benchmark plays 15 s of music with the screen open. Copy the report from the dialog.
- Safety improvements: verify output MP4 has video and audio samples, reject unsupported H.264 output profiles before rendering, and delete newly created empty/partial SAF documents on failed copy.
- Offline CI checks cover `FrameMetrics` and `ExportProfile`. Build success is **not** a Poco F6 performance measurement. CPU/GPU, thermals and FPS on that specific phone must be tested with the installed app.

### Testing matrix for Poco F6 (requires the physical phone)

1. Ensure battery > 40%, disconnect charging, remove battery-saver mode; let device cool to ambient.
2. Open a consistent 60 s local MP3/WAV and a background image. Keep default effects and compare preview benchmark in ECO 30 FPS vs 60 FPS.
3. Export 30 s from the same project in 720p/30, 1080p/30 full, 1080p/30 efficient and (if supported) 1080p/60. Note real wall-clock throughput, p95 frame-time, Android thermal severity, output file size, and battery consumption. Stop if Android reports CRITICAL or worse, or the device becomes too hot to hold.
4. Verify MP4 on a separate video player: opening frames, audio onset sync, drop alignment, correct final duration, and no black frames or silent ending.
5. Copy the **Performa** report so regression between versions is measurable. CI/emulator cannot establish actual Poco F6 temperature or FPS.

### Android emulator integration smoke tests (separate workflow)

`.github/workflows/android-emulator-smoke.yml` builds a Pixel 4 emulator (API 34)
with Android SDK, launches the editor and generates a three-second 110 Hz WAV
with 2 Hz rhythmic dynamics. It decodes/analyzes the synthetic audio, renders
90 frames of reactive dust/glow, encodes H.264 + AAC and checks that the final
MP4 has **both** tracks. This does not validate thermals or FPS of Poco F6.
The Google APIs emulator depends on virtual acceleration and software GL.
