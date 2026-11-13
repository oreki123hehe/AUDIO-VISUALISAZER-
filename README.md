# Ranneki Reactive Studio

Native offline Android music-visual editor, version **0.11.0-motion-beta**. The fixed spectrum has become a layered studio with attached cropped logos, independent audio connections and a motion engine.

## Fast workflow

Import **Lagu**, choose one of **12 Templates**, attach a **Logo**, and export MP4. Templates assign motion and separate audio responses automatically. Music/background/logo are preserved when switching templates; Undo restores the previous arrangement.

**+ Visualizer** opens a gallery with real mesh thumbnails for **24 styles**:

| Family | Styles |
| --- | --- |
| Rings and orbits | Ring Spectrum, Ring Wave, Orbit Flower, Dotted Ring, Twin Rings, Spiral, Sunburst, Petal Loop, Arc Spectrum, Radial Ticks |
| Bars and waves | Spectrum Bars, Mirror Bars, Wave Spectrum, Dot Matrix, LED Columns, Ribbon |
| Geometry and symbols | Square, Diamond, Triangle, Hexagon, Star, Heart, Infinity, Lissajous |

Styles are frequency-driven designs; Wave Spectrum and Lissajous are not raw PCM oscilloscopes. Up to **48 total layers** share the limit across visualizers, effects, images, video and text. Logos center-crop and follow their element's transform. Automatic crop matches geometric styles; circle, rounded square, square, triangle, hexagon, diamond, heart and star masks are also selectable.

## Motion

- Position, scale, rotation and other continuous visual controls retain fractional values during animation; they are no longer rounded to whole slider steps each frame.
- Nine keyframe timing modes: Linear, Ease In, Ease Out, Ease In Out, Spring, Bounce, Overshoot, Hold and Custom Bezier. The curve editor has draggable Bezier handles and can apply a curve to one segment or every segment in that parameter. Keyframe endpoints retain the existing integer editor values; interpolation is subpixel. Absolute parameter safety limits still apply.
- **Gerak & Motion Blur** provides nine animated presets plus Off: Kick Punch, Spring Zoom, Bounce Zoom, Float, Orbit, Spin, Sway, Beat Tilt and Cinematic Drift. Spring/Bounce use a damped response computed in source-audio time. Speed adjusts spring frequency or continuous-motion speed; it does not retime the soundtrack.
- Layers can inherit another layer's position, rotation and scale. Cycles are rejected, including during project import. Removing a parent detaches its children. Parenting uses layer centers; arbitrary anchor points, null objects and a true 3D camera are not implemented.
- Per-layer transform motion blur uses nine spatial exposure samples and a fixed approximately 17 ms look-back, scaled by its intensity. It includes translation, rotation and scale (including inherited motion and camera shake). It does not resimulate previous particle shapes, video frames or spectrum deformation; very rapid rotation/large displacement may need lower intensity. Preview, GPU PNG and MP4 use the same effect path.

## Ordered layer effects

**Efek berurutan** adds up to six filter instances per layer, in editable order: Blur, Bloom, RGB Split, Wave Warp, Saturation and Exposure. Every filter has its own optional audio source, signed depth, custom frequency limits, attack and release. Neutral saturation/exposure is 50. Motion blur runs first, then the listed filters, then the layer's final Normal/Add/Screen/Multiply blend. Two reusable transparent GPU framebuffers avoid allocating a separate full-resolution texture for every layer. Filters and blur increase GPU work; no physical sustained-FPS guarantee is made.

## Audio and projects

Each layer has its own kick/bass/snare/mid/treble/custom-frequency response. **Audio → Parameter** connects visual parameters independently; modulation adds to manual/keyframed values. Sixteen parameter tracks are available. Main waveform analysis uses FFT bands and onset estimates, not isolated instruments.

**Audio Terpisah** accepts four optional, already-separated local stem files: vocal, drums, bass and melody. Align them to the original soundtrack's starting timestamp. They drive visuals; the original soundtrack remains the MP4 audio. Automatic stem separation and automatic intro/build/drop/outro arrangement are not implemented.

Version 5 project JSON stores motion, parent relationships, curves, ordered filters, logo crops and independent audio routes. Older projects/keyframes still load. Portable ZIP embeds logo/media/stem assets. Session autosave, media restoration and 30-state Undo/Redo are available; missing files or revoked permissions can require reimport.

## Rendering and verification

GLES2 composes standard layers in order, including masks and premultiplied-alpha blending. PNG and MP4 use the same GPU path. Economy/balanced output applies reduced resolution to the GPU framebuffer. Video uses MediaMetadataRetriever/bitmap uploads (preview up to 1280 long edge; export bounded by source and 4096 pixels), not zero-copy decode. The old Canvas compatibility path does not implement the new GPU filter stack.

The build workflow runs **25 pure-Java tests**, including subpixel/eased keyframes, Bezier inversion, spring settling, parent composition, filter serialization and all 24 peak-density meshes. Software-EGL pixel tests execute the real shaders: logo orientation/crops, blending/opacity, neutral filters, saturation and direction of motion blur. Android emulator smoke tests cover all 24 styles, motion/filter/parent persistence, invalid parent-cycle rejection, GPU PNG, portable logo/stem ZIP, mixed-media composition and H.264/AAC export. The emulator job also captures the editor startup UI.

JDK 17, Gradle 8.9, SDK 35 / build-tools 35.0.0. Build with `gradle :app:assembleDebug`, or download the APK artifact from **Build Android APK**. Minimum API 26. No runtime server or Termux.

## Remaining scope

This beta implements specific motion and audio-reactive tools, not complete feature parity with Avee Player, Alight Motion or CapCut. Automatic stem separation, song-section detection, full multitrack video/audio editing, direct freeform feathered masks, arbitrary anchors/motion paths/3D cameras, zero-copy video, resumable/background exports and sustained physical Poco F6 performance/thermal testing remain outside this revision. Orientation changes preserve the Activity/export, but export cannot resume after Android terminates the process.
