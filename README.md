# Ranneki Visualizer Studio — Android V1

**Offline-first, native Android audio-reactive visualizer.** Prototipe awal untuk Poco F6 dan perangkat Android 8+.

## Fitur yang tersedia dalam source V1

- Pilih lagu MP3/WAV lewat Android file picker (sesuai decoder perangkat).
- Pilih gambar latar anime/foto melalui Android file picker.
- Analisis audio offline dengan FFT untuk bass, mid, treble, dan estimasi onset kick/snare.
- Putar musik dan lihat preview visualizer yang sinkron menggunakan posisi MediaPlayer.
- Empat layer custom: **debu**, **api**, **hujan**, **water ripple**.
- Setiap layer: sakelar ON/OFF, sumber reaktivitas Kick / Bass / Kick + Bass / Treble / Snare, pengaturan ukuran, density, dan sensitivitas.
- Kanvas 9:16 / 16:9, simpan preset ke penyimpanan lokal.
- Simpan satu frame PNG ke galeri pada Android 10+.
- **Tidak meminta izin Internet, mikrofon, dan tidak memakai server saat digunakan.**

**Penting:** Ini masih prototipe. FFT memakai beberapa rentang frekuensi dan estimasi onset; kick/snare belum setara pemisahan stem AI. Ekspor MP4, timeline editing, shader 3D, dan plugin efek belum tersedia. Aplikasi juga belum diverifikasi langsung pada Poco F6.

## Cara mendapatkan APK tanpa PC / Termux

1. Buka tab [Actions](../../actions).
2. Pilih **Build Android APK**.
3. Jika tidak berjalan otomatis, klik **Run workflow** pada branch `main`.
4. Setelah status hijau, buka run dan unduh artifact **RannekiVisualizerStudio-debug-apk**.
5. Ekstrak file ZIP artifact untuk mendapatkan `app-debug.apk`, lalu instal di Android.

GitHub Actions hanya diperlukan untuk **kompilasi APK**. Setelah terpasang, aplikasi dirancang untuk berfungsi offline.

## Build sendiri

Java 17, Android SDK Platform 35, Gradle 8.9:

```bash
gradle :app:assembleDebug
```

APK akan tersedia di `app/build/outputs/apk/debug/app-debug.apk`.

Tes FFT lokal:

```bash
javac -d out app/src/main/java/com/ranneki/visualizer/Fft.java tests/SmokeTest.java
java -cp out com.ranneki.visualizer.SmokeTest
```

## Rencana berikutnya

- V1.1: Ekspor MP4 dengan MediaCodec + MediaMuxer.
- V1.2: Custom frequency band, threshold, attack/release, koreksi kick/snare manual.
- V2: Bebas membuat layer, blend modes, shader GPU, dan timeline.
- V3: Particle 3D, stem separation AI opsional, advanced FX.

Kinerja partikel dan rendering tetap bergantung pada CPU/GPU HP. Mode preview ringan dan batas jumlah partikel akan menjadi bagian pengoptimalan.
