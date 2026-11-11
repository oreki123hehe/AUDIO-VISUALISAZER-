# Ranneki Visualizer Studio — Android V1.4 development

Native Java Android visualizer. Offline rendering and analysis on the device (no server, Termux, internet, or API required at runtime). Target phone Poco F6. This is an **experimental build, not yet field-tested on a device**.

## V1.4 functionality

- Android SAF audio import (MP3/WAV/AAC if Android platform codecs support it), background image import, MediaPlayer playback, offline 32-band FFT analysis, approximated kick/snare/bass/mid/treble, per-layer custom Hz ranges.
- **Dynamic FX layer editor**: up to 48 independent instances from ten built-in FX types (dust, fire, rain, water ripples, smoke, snow, sparks, lightning, glow, glitch). Add/duplicate/delete, enable, order, and customize each independently. Independent particles/ripple state, beat source, density, size, strength, velocity, direction, alpha, threshold, attack/release, blend/mask, per-property routes, and animated strength keyframes.
- Project JSON serialized into local SharedPreferences as a single preset (including layer order / independent settings). Does not yet include image source assets; import background manually if the app relaunches.
- **Export MP4 offline with audio** via Android `MediaCodec` H.264 Surface encoder + OpenGL ES texture upload from the deterministic frame-by-frame Canvas renderer, and Android `MediaMuxer` output. AAC inputs are re-muxed if compatible; MP3/WAV/non-AAC are decoded and transcoded to AAC-LC, then muxed. Default 720p30; also 540p30 and experimental 1080p30. Save with Android `ACTION_CREATE_DOCUMENT`, progress and tap-to-cancel. Export may consume significant CPU/GPU, memory, storage, and battery.
- Export uses a copied scene state independent of live preview; visuals advance through analysed timestamps in increasing frame order; resulting video timestamp is frame-index-based (30 fps), and audio timestamps are copied or normalized from source. Export expected to support common Android codec implementations, but not verified on a physical Poco F6.

## Important limitations

- **No field test on a real Android phone yet**. CI compile/test success does not guarantee hardware codec and EGL behavior. Need test with AAC, MP3, WAV and various sample rates (including audio silence / skipped PTS).
- Layer editor currently covers **built-in FX only**; arbitrary image/text/video layers, multi-track timeline editor, custom GLSL shaders, and programmable masks are not implemented. Strength keyframes exist, other properties use manual values/audio routing; broader keyframing not yet complete.
- Export supports fixed aspect-ratio resolutions and 30 FPS. It's not a GPU-only pipeline: CPU Canvas draws each frame to Bitmap before uploading to the GPU encoder surface, potentially generating heat. File size and temporary storage usage can be large; 1080p is experimental.
- Input formats ultimately depend on Android platform codec support. >2-channel non-AAC sources currently not supported; PCM conversions limited to standard PCM16/8/24/32 and float. A failed export may leave an empty SAF document; temporary cache files are cleaned.
- No audio mastering; AAC transcode uses ~192 kbps. Audio sync and visual orientation require device validation before release.

## Build Android debug APK

Open [GitHub Actions](../../actions), select **Build Android APK**, download `RannekiVisualizerStudio-debug-apk` artifact from a successful run. Uses JDK17, Android SDK35, Gradle8.9 and unit tests for FFT/envelopes/strength keyframes.

## Tests

```bash
mkdir -p build/pure-tests
javac -d build/pure-tests \
  app/src/main/java/com/ranneki/visualizer/{Fft,BandSpectrum,ReactiveEnvelope,AutomationTrack}.java \
  tests/{SmokeTest,AudioReactiveTest,AutomationTrackTest}.java
for test in SmokeTest AudioReactiveTest AutomationTrackTest; do java -cp build/pure-tests com.ranneki.visualizer.$test; done
```

## Follow-up priorities

1. Test audio sync, correct video orientation, cancel/error handling and Android hardware encoder availability on Poco F6.
2. Efficient GPU offscreen compositing to reduce CPU Bitmap/texture upload burden.
3. Arbitrary image/text/video layers, expanded keyframes, waveform multi-track timeline, freeform masks, JSON file import/export, 60 FPS and robust rendering presets.
