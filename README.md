# Ranneki Reactive Studio

Native offline Android music-visual editor, **0.12.0-studio-beta**. Combine up to **48 visual layers**, **29 visualizer styles**, attached cropped logos, audio-driven motion, drawn paths and a perspective camera. This revision expands editing and automatic synchronization; it adds no export features.

## Start with automatic synchronization

1. Import **Lagu**, choose one of **12 Templates**, then attach a **Logo**. Switching templates preserves imported artwork and soundtrack; Undo restores the previous arrangement.
2. Open **Studio+ → Pisahkan audio otomatis + sinkronkan layer**. Four bundled UMX-HQ neural networks estimate vocals, drums, bass and other instruments entirely on the device. Text, images, effects and visualizers receive independent audio routes, and Auto Arrange is enabled.
3. Use **Bagian lagu & Auto Arrange** to review the estimated intro, verse, build, drop and outro. Labels and boundaries are editable; section changes blend intensity, opacity and scale smoothly.
4. Use **Audio → Parameter** on any layer to assign different sources to individual properties. The existing frequency/onset controls remain available alongside the four estimated stems.

Separation is processing, not live playback. It runs in bounded chunks with cancellation and disk-backed stereo audio. The four bundled models add approximately 93 MB before APK compression; first use also copies the models into app storage. Additional space is needed for four stereo WAV stems. Model estimates can contain leakage and artifacts, and “Instrumen lain” is not a pure melody track. Song-section labels are editable energy/onset estimates, not guaranteed semantic analysis. No server, account, Internet permission or Termux is required.

## Visualizers and artwork

**+ Visualizer** opens a mesh-thumbnail gallery:

| Family | Styles |
| --- | --- |
| Rings and orbits | Ring Spectrum, Ring Wave, Orbit Flower, Dotted Ring, Twin Rings, Spiral, Sunburst, Petal Loop, Arc Spectrum, Radial Ticks |
| Bars and waves | Spectrum Bars, Mirror Bars, Wave Spectrum, Dot Matrix, LED Columns, Ribbon |
| Geometry and symbols | Square, Diamond, Triangle, Hexagon, Star, Heart, Infinity, Lissajous |
| PCM, stereo and custom | PCM Waveform, PCM Stereo, Stereo L/R, Drawn Spectrum, Morph Spectrum |

The first 24 styles use frequency-driven geometry. The two PCM styles preserve signed waveform samples; Stereo L/R uses independently analyzed channels. Draw spectrum shapes A/B in **Kanvas • Path • Pivot • Klip**, then morph manually or with an audio source. Paths are resampled by distance to distribute bars evenly.

Attached logos stay upright, center-crop to the selected shape, and inherit their visualizer's transform. Circle, rounded square, square, triangle, hexagon, diamond, heart and star crops are available. Newly imported backgrounds are full-canvas image layers with their own motion, effects and audio routes. Older projects retain their original backdrop until a new background is imported.

## Motion and direct editing

- Sixteen parameter tracks with subpixel interpolation; nine timing modes include Spring, Bounce, Overshoot, Hold and editable cubic Bezier curves.
- Nine automatic motion presets plus Off: Kick Punch, Spring Zoom, Bounce Zoom, Float, Orbit, Spin, Sway, Beat Tilt and Cinematic Drift.
- Layer parenting and invisible control/null layers. Cycles are rejected; arbitrary pivot points affect rotation and scaling.
- Finger-drawn motion paths with loop, ping-pong or one-shot playback, easing and optional tangent-following rotation. Splitting a clip preserves its motion phase.
- Canvas controls pause playback for editing: drag position, use corner/rotation handles, or switch to **PIVOT** to place the anchor. Position dragging accounts for camera perspective and parent transforms. Existing parameter slider bounds still apply.
- Camera X/Y/Z, yaw, pitch, roll and focal distance project flat layer planes at independent depths. Orbit, dolly and audio-punch camera motion are available. This is perspective composition of 2D artwork, not a 3D mesh/model editor.
- Draw closed masks and adjust feathering. Feathering softens the rendered alpha edge, including artwork edges.

## Ordered effects and motion blur

Each layer supports up to **12 ordered filter instances**, chosen from **22 effects**: Blur, Bloom, RGB Split, Wave Warp, Saturation, Exposure, Directional Blur, Zoom Blur, Radial Blur, Twirl, Pixelate, Mirror, Kaleidoscope, Posterize, Invert, Sepia, Contrast, Hue, Sharpen, Scanlines, Lens and Displacement. Each filter can have its own audio source, signed depth, frequency range, attack and release.

Temporal motion blur resamples still artwork, spectrum geometry and particles at the current time and two earlier shutter times. Video retains transform-based blur; it does not decode previous video images for each shutter sample. Blur runs before the ordered effects and final Normal/Add/Screen/Multiply blend. Three reusable transparent GPU framebuffers bound intermediate target count; more effects and blurred layers still increase GPU work.

## Multitrack and projects

**Studio+ → Multitrack audio / suara video** provides up to **16 editable audio clips**. Import audio or a video's audio track, drag placement/trim handles, or set exact in/out points, speed, volume, pan, mute and fade times. Split at the playhead and join compatible adjacent clips. Overlap clips with fades for crossfades. **Terapkan mix** renders a disk-backed stereo mix and analyzes it for preview; edit the retained source clips and apply again to update it. Speed currently changes pitch. Peak attenuation protects the mixed output from clipping.

Visual video/image layers have timeline placement, source in/out, speed, looping and fade transitions. Their split operation preserves media and motion phase; join rejects differing effects/keyframes/settings. Import video audio into the mixer when it should contribute to the soundtrack.

Version 6 JSON preserves advanced layer controls, camera, sections, curves, effects and audio routes, while older projects still load. Session autosave and portable project ZIP also preserve the multitrack audio list; ZIP embeds referenced media and stems. Thirty-state Undo/Redo covers scene/session edits. Revoked file permissions or missing external media may require reimport.

## Build and verification

Use JDK 17, Gradle 8.9 and Android SDK 35 / build-tools 35.0.0. Minimum API 26; APK supports arm64-v8a and x86_64. ONNX Runtime Android is pinned to 1.20.0.

Before building, generate the licensed UMX-HQ assets:

```sh
python3 -m venv build-model-env
build-model-env/bin/pip install torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu
build-model-env/bin/pip install onnx==1.17.0 onnxruntime==1.20.1 numpy
build-model-env/bin/python tools/prepare_separation.py
gradle :app:assembleDebug
```

Use Python 3.12 for the pinned conversion environment. Conversion verifies source hashes and checks three sequence lengths against the original PyTorch networks. Only dense MatMul operations are quantized; LSTM weights remain float. CI caches the generated assets, and Android preBuild rejects missing models. See [THIRD_PARTY.md](THIRD_PARTY.md) for model and runtime attribution.

The build runs **28 pure-Java tests** and software-EGL pixel tests covering actual shader orientation, premultiplied blending, logo crops, perspective projection, effects and motion-blur direction. `tests/OfflineStemTest.java` exercises the real Java ONNX engine on a Linux host, checking stereo alignment, exact length and reconstruction across chunk boundaries. The Android integration job additionally exercises actual codecs, four-stem inference, multitrack mix, portable sources, new camera/path state, all effects, visualizer meshes and the existing rendering pipeline; its report and startup screenshot are artifacts of that workflow.

**Studio+ → Uji ketahanan preview 5 menit** measures preview pacing and Android thermal status on the device while looping the current song. GPU timings are CPU submission timings, not GPU kernel execution. No sustained physical Poco F6 result or universal 60 FPS guarantee is claimed. Video currently uses MediaMetadataRetriever/bitmap uploads rather than zero-copy decoding.

This beta implements specific editing and audio-reactive tools. Complete Avee Player, Alight Motion or CapCut feature parity has not been established. Full quality/performance validation on real songs and physical devices remains necessary; export improvements are outside this revision.
