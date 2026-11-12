# Ranneki Reactive Studio

Native offline Android music-visual editor. Version `0.10.0-studio-beta` replaces the fixed spectrum with independent visualizer layers, attached cropped logos and audio connections for each visual parameter.

## Start a project

1. Import the original soundtrack using **Lagu**.
2. Select **Template**: Neon Pulse, Montagem Impact, Dual Frequency, Cinematic Orbit, Clean Bars or Flow Waves. Templates assign separate audio responses, preserve the first existing logo and support Undo.
3. Tap **Logo** and choose its visualizer. The image is center-cropped to a circle, rounded square or square and follows that element's motion.
4. Use **+ Visualizer** for Ring Spectrum, Ring Wave, Spectrum Bars, Mirror Bars, Waveform or Orbit Flower. Each has independent gradient colors, glow, frequency bounds, transforms and timeline range.
5. Export MP4. **Proyek & Alat** contains GPU PNG, portable project ZIP, JSON presets, custom GLSL and diagnostics.

The limit is **48 total layers**, shared between visualizers, built-in FX, text, images and muted video. Advanced controls are created only when opened.

## Audio routing

Each layer retains its primary kick/bass/snare/mid/treble/custom-frequency response. **Audio → Parameter** additionally connects size, amount, velocity, opacity, X/Y, scale, rotation, mask radius or direction to a separate source. Connections have their own signed modulation depth, custom frequency bounds, attack and release. Modulation is added to manual/keyframed values and clamped. The 16 existing numeric keyframe tracks remain available.

Envelopes are evaluated in source-audio time and cached: seeking or changing preview frame rate does not change the evaluated route.

**Audio Terpisah** accepts four optional local stem files: vocal, drums, bass and melody. They must already be separated and share the soundtrack's starting timestamp. Stems drive visuals independently; only the original soundtrack is muxed into MP4. Stem audio and attached logos travel inside portable ZIP projects.

FFT bands and onset estimates are not isolated instruments. Automatic separation of a mixed song into clean stems is **not implemented**. The Waveform style is a frequency contour, not a PCM oscilloscope.

## Rendering and editing

- GLES2 composition supports ordered effect/media/visualizer layers, masks, blending, transforms and custom postprocessing. PNG uses the same GPU scene and postprocessing through a private EGL pbuffer.
- Media texture orientation and premultiplied-alpha Screen/Multiply blending are corrected. Attached logos use local shape masks and center cropping.
- Economy/balanced render resolution applies to the actual GPU framebuffer before output upscaling.
- Video preview decodes asynchronously up to a 1280-pixel longest edge without the previous 95 ms throttle. Export requests render-target-sized frames, bounded by 4096 pixels and source dimensions. Decoding still uses MediaMetadataRetriever and bitmap uploads, not zero-copy SurfaceTexture.
- Edits autosave with 30-state Undo/Redo within the session. Restoration includes main audio/background references, layer media, stems, shader and postprocessing. Deleted/moved files or revoked URI permissions can require reimport; ZIP embeds assets.
- Orientation changes preserve the Activity and an in-progress render. Export is not a foreground service and cannot resume after process termination.
- Removed layer textures are pruned. Published GPU frames retain valid image references during edits.

## Verification

The build workflow runs pure-Java audio, route, keyframe, mesh, history, crop, PCM, timebase and export tests. `tests/gpu_visual_regression.py` exercises actual GLES shaders in software EGL: orientation, normal/Screen/Multiply opacity, premultiplied alpha and circular center crop.

The Android debug-only SmokeTestActivity verifies GPU PNG logo orientation/crop, multiple spectrum styles, stem-route persistence, logo/stem ZIP packaging, H.264/AAC export, reduced GPU render resolution and mixed image/text/video/effect composition.

The user reports an earlier APK ran smoothly on their phone. That does not measure this revision's sustained 1080p60 performance. Physical-device thermals and long exports must be validated separately from CI/software EGL.

## Build

JDK 17, Gradle 8.9, SDK 35 / build-tools 35.0.0. Run `gradle :app:assembleDebug`, or download `RannekiVisualizerStudio-debug-apk` from **Build Android APK**. Minimum API 26. No runtime server or Termux.

## Remaining scope

This beta is not feature-equivalent to an entire professional editor. Automatic stem separation, directly drawn feathered masks, per-layer filter stacks, full multitrack video/audio editing, zero-copy video decode, resumable/background exports and physical sustained-performance verification remain separate work. There is no claim of exceeding Avee Player, Alight Motion or CapCut in every feature or quality metric.
