# Ranneki Visualizer Studio — Android V1.3 (development)

Aplikasi Android native untuk membuat visualizer musik audio-reactive **offline** tanpa server dan tanpa Termux saat digunakan. Kode memakai Java, Android Canvas, MediaCodec + MediaExtractor untuk analisis file musik, serta MediaPlayer untuk preview. Target perangkat awal: Poco F6, belum diuji langsung pada perangkat.

## Sudah diimplementasikan (kode aplikasi)

- Import audio melalui Android Storage Access Framework, analisis MP3/WAV dan format lain yang dikenali decoder perangkat, serta import gambar sebagai background.
- Analisis offline FFT 2048 / hop 512 / decimate 2 dengan 32 band logaritmis antara 25–10.000 Hz (dibatasi sample rate audio).
- Perkiraan onset kick/snare serta energi bass, mid, dan treble. **Bukan pemisahan stem / pengenalan instrumen akurat.**
- 10 layer efek yang dapat diaktifkan dan dipindahkan urutannya: dust, fire, rain, ripple, smoke, snow, sparks, lightning, glow, glitch.
- **Per FX**: sumber ritme utama; threshold/attack/release, ukuran, kepadatan, reaktivitas, opacity, kecepatan, custom min/max Hz.
- **V1.3** routing independen untuk **ukuran, kepadatan, kecepatan, opacity** ke sumber kick/bass/kick+bass/snare/mid/treble/custom Hz, atau mode Manual.
- **V1.3** pilihan blending per layer Normal/Add/Screen/Multiply, mask sederhana (circle/top/bottom) dan ukuran mask, arah gerak partikel.
- **V1.3** keyframe linear untuk **strength** tiap layer berdasarkan posisi lagu; editing melalui tombol Add/Remove/Clear di posisi scrubber.
- Spectrum melingkar berbasis 32 band, camera shake berbasis kick, format preview 9:16 / 16:9, mode ECO 30 FPS atau quality 60 FPS.
- Simpan satu preset di SharedPreferences perangkat, termasuk mapping, keyframe, layer order, dan opsi efek; simpan frame PNG di galeri.
- Pengujian matematika via JDK dan build Debug APK via GitHub Actions.

## Batasan saat ini

- Editor masih 10 tipe FX bawaan; belum bisa duplikasi layer arbitrer, import efek pihak ketiga, atau masking gambar bebas.
- Timeline saat ini **scrubber + keyframe strength**, belum keyframe semua properti, multi-track timeline, atau waveform timeline.
- **Belum ekspor MP4**, audio mastering, video background, video-layer, dan GPU shader editor; hanya preview serta simpan frame PNG.
- `saveLayer` untuk efek blend/mask menambah beban GPU; aktifkan ECO dan batasi efek simultan pada HP. Animasi Canvas masih tergantung performa perangkat dan perlu profiling.
- PCM, MP3, WAV, dan codec audio tertentu bisa berbeda hasil menurut decoder Android. Pengujian perangkat fisik dan uji regresi masih diperlukan.

## Cara build APK (GitHub Actions)

1. Buka **Actions → Build Android APK** di repository ini.
2. CI menginstal SDK melalui `android-actions/setup-android@v4` (v3 gagal akibat paket `tools` yang sudah dihapus), lalu menjalankan tes Java dan `gradle :app:assembleDebug`.
3. Saat build sukses, ambil artifact `RannekiVisualizerStudio-debug-apk`.
4. APK bertanda **debug**, bukan paket rilis produksi; jangan menganggap sudah stabil hanya karena kompilasi sukses.

## Tes JDK tanpa Android SDK

```bash
mkdir -p out
javac -d out app/src/main/java/com/ranneki/visualizer/{Fft,BandSpectrum,ReactiveEnvelope,AutomationTrack}.java tests/{SmokeTest,AudioReactiveTest,AutomationTrackTest}.java
java -cp out com.ranneki.visualizer.SmokeTest
java -cp out com.ranneki.visualizer.AudioReactiveTest
java -cp out com.ranneki.visualizer.AutomationTrackTest
```

## Roadmap bertahap

1. **V1.3**: perbaikan CI; pengaturan detail efek, mask/blend, keyframe strength; verifikasi build.
2. **V1.4**: model layer dinamis (duplikasi/reorder per-instance), timeline dengan multi-keyframe di semua parameter, preset JSON import/export.
3. **V1.5**: renderer deterministik frame-by-frame untuk export video MP4 + audio AAC yang sinkron, uji dan profiling Android nyata. Ekspor tidak dijanjikan ringan hanya karena GPU tersedia.
4. **V2**: kompositor OpenGL ES, shader, editor mask kompleks, performance profiling dan fitur visual lanjutan.

Dukungan offline berarti **tidak ada server saat menjalankan aplikasi**; GitHub cloud hanya dipakai saat kompilasi. Analisis/render video tetap memakai CPU/GPU HP dan dapat meningkatkan suhu.
