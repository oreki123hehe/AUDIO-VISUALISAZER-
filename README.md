# Ranneki Visualizer Studio — Android V1 (source project)

**Status:** Prototype source code; belum dikompilasi menjadi APK / diuji pada perangkat nyata.

Aplikasi Android native, tanpa backend/server, tanpa Termux saat penggunaan, tanpa izin mikrofon maupun Internet. Dibangun menggunakan Java dan Android Canvas yang diakselerasi GPU. Audio dianalisis secara offline dari file yang dipilih pengguna (bukan mendengarkan mikrofon).

## Fitur V1 yang sudah diimplementasikan

- Import audio menggunakan pemilih file Android (contoh: MP3/WAV, bergantung decoder yang tersedia pada perangkat).
- Import gambar background dari penyimpanan Android.
- Analisis FFT audio secara offline: bass (25–180 Hz), mid (180–2.000 Hz), treble (2.000–8.000 Hz efektif dibatasi Nyquist), estimasi onset kick dan snare.
- Playback lagu + responsive preview melalui jam pemutaran MediaPlayer.
- Empat layer: **Dust**, **Fire**, **Rain**, **Water Ripple**. Partikel dan efek bereaksi terhadap fitur musik yang dipilih.
- Setiap layer punya toggle ON/OFF, sumber reaktivitas (Kick, Bass, Kick + Bass, Treble, Snare), slider **Ukuran**, **Jumlah/Density**, dan **Reaktivitas**.
- Background otomatis dipotong center-crop, mode kanvas **9:16 / 16:9**.
- Preset disimpan lokal ke SharedPreferences.
- Simpan **frame PNG** ke galeri (`Pictures/RannekiStudio`) pada Android 10+.

**Batasan:** Deteksi kick/snare saat ini adalah *estimasi onset spektral*, bukan pemisahan stem/instrumen berbasis AI; akurasinya dapat bervariasi menurut lagu. Sebagian frekuensi saling tumpang tindih. V1 belum punya timeline editor, shader 3D, ekspor MP4, ekspor audio, ataupun custom effect chaining penuh. Belum ada APK siap-instal yang terverifikasi.

## Susunan proyek

```
RannekiVisualizerStudio/
  .github/workflows/build-apk.yml
  app/
    build.gradle
    src/main/AndroidManifest.xml
    src/main/res/values/styles.xml
    src/main/java/com/ranneki/visualizer/
      MainActivity.java
      ReactiveView.java
      AudioAnalyzer.java
      Fft.java
  tests/SmokeTest.java
  build.gradle
  settings.gradle
  gradle.properties
```

## Build Android APK lewat GitHub Actions (HP tidak mengompilasi)

1. Buat repository GitHub, misalnya `RannekiVisualizerStudio`.
2. Masukkan **isi folder proyek ini** (bukan sekadar file ZIP) ke root repository, termasuk `.github/workflows/build-apk.yml`. Pengunggahan source dapat dibantu oleh GitHub connector bila repository dan akses sudah tersedia.
3. Push/commit ke branch `main`, atau buka tab **Actions → Build Android APK → Run workflow**.
4. Pada run yang sukses, buka bagian **Artifacts → RannekiVisualizerStudio-debug-apk**.
5. Download ZIP artifact, ekstrak `app-debug.apk` dan instal di HP, dengan mengizinkan instalasi dari sumber tersebut bila Android meminta.

GitHub Actions memasang JDK 17, Android SDK 35 dan Gradle 8.9 untuk menjalankan `:app:assembleDebug`. Dibutuhkan Internet untuk build dan unduh APK, **bukan untuk menjalankan aplikasi**. Workflow menggunakan layanan CI GitHub, bukan server aplikasi yang perlu disewa.

> Jika Android memperingatkan bahwa APK berasal dari sumber luar Play Store, pastikan proyek dan repository benar-benar milikmu. Build V1 menghasilkan debug APK, bukan paket Play Store resmi yang ditandatangani untuk rilis.

## Build di Android Studio / Gradle

JDK 17, Android SDK Platform 35, Gradle 8.9:

```
gradle :app:assembleDebug
```

APK (bila build sukses): `app/build/outputs/apk/debug/app-debug.apk`.

## Smoke test FFT tanpa Android SDK

```
javac -d out app/src/main/java/com/ranneki/visualizer/Fft.java tests/SmokeTest.java
java -cp out com.ranneki.visualizer.SmokeTest
```

Tes ini **sudah berhasil dijalankan** di lingkungan pembuatan source. XML dan parsing Java juga lolos pemeriksaan sintaks. Belum ada bukti bahwa keseluruhan proyek Android telah dikompilasi atau berjalan di Poco F6.

## Rencana pengembangan

- V1.1: Ekspor video MP4 + audio secara offline melalui MediaCodec/MediaMuxer; pastikan audio/video sinkron dan uji panas perangkat.
- V1.2: Deteksi beat lebih presisi (multi-band spectral flux), onsets yang bisa dikoreksi manual, mapping attack/release/threshold per efek, custom frequency ranges.
- V2: Layer bebas, masking, opacity/blend modes, shader GPU, timeline, preset sharing.
- V3: Partikel 3D dan analisis stem terpisah (opsional), jika performa perangkat memungkinkan.

Tidak diperlukan koneksi server saat penggunaan V1. Analisis audio + preview/efek dilakukan pada perangkat. HP tetap menggunakan CPU/GPU dan bisa hangat ketika menganalisis lagu atau menggambar banyak partikel; gunakan density yang lebih rendah bila perlu.
