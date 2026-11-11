# Ranneki Visualizer Studio — Android V1.2 (work in progress)

Aplikasi Android native untuk membuat visualizer reaktif musik **tanpa server dan tanpa Termux ketika digunakan**. Source menggunakan Java, Android Canvas, MediaCodec + MediaExtractor untuk analisis file musik secara offline, dan MediaPlayer untuk preview.

## Apa yang sudah diimplementasikan

- Impor musik lewat pemilih file Android; analisis MP3 / WAV dan format lain yang didukung decoder di perangkat.
- Impor gambar anime/foto sebagai background, center crop 9:16 atau 16:9.
- **FFT 2048 / hop 512 / decimate 2**, deteksi energi bass, mids, highs, serta perkiraan transient kick dan snare.
- **32 pita spektrum logaritmis (25–10.000 Hz)** untuk sumber `Frekuensi custom` pada tiap layer. Rentang tertinggi dibatasi Nyquist file aslinya.
- **10 efek audio reactive**: debu, api, hujan, air/ripple, asap, salju, percikan, petir, glow, glitch.
- Tiap efek dapat di-ON/OFF dan diurutkan melalui tombol ↑ dan ↓; pengaturan masing-masing mencakup:
  - Sumber: Kick, Bass, Kick+Bass, Treble, Snare, Frekuensi custom, Mid.
  - Ukuran, Jumlah, Reaktivitas, Opacity, Kecepatan.
  - Threshold 0–95%, Attack 0–1.000 ms, Release 0–1.500 ms.
  - Rentang frekuensi Min/Max custom, 25–10.000 Hz.
- Spectrum melingkar menggunakan 32 pita frekuensi, bisa dimatikan dan diubah ukurannya. Kamera punya efek shake yang mengikuti hentakan kick.
- **Mode ECO 30 FPS** sebagai default preview hemat beban (pengurangan densitas partikel); **QUALITY 60 FPS** opsional. Ini mitigasi beban, bukan jaminan HP tidak panas.
- Analisis file audio sebelumnya dibatalkan saat pengguna memilih file baru (agar tidak menghabiskan CPU sia-sia).
- Preset lokal menyimpan urutan layer, kondisi masing-masing efek, serta seluruh slider dan peta frekuensi. Ekspor screenshot frame PNG.

## Yang masih dalam proses

- **Belum ekspor video MP4 bersuara**, editing timeline, video layer, masking, renderer shader 3D, stem separation AI, maupun sistem custom effect/plugin yang tak terbatas.
- Kick/snare adalah **deteksi perkiraan**, bukan isolasi instrumen terpisah. Beberapa timbre dan frekuensi bertumpuk; perlu koreksi manual saat hasil tidak tepat.
- Program belum diuji langsung pada Poco F6. Build Android perlu dijalankan lewat GitHub Actions untuk menilai kompilasi serta performa aktual.

## Build APK via GitHub Actions

1. Source proyek di-root repository GitHub, termasuk `.github/workflows/build-apk.yml`.
2. Buka **Actions → Build Android APK**. Workflow juga aktif pada push ke `main`.
3. Jika run sukses, ambil artifact `RannekiVisualizerStudio-debug-apk` yang berisi `app-debug.apk`.
4. Debug APK hanya untuk pengujian; tidak setara rilis resmi aplikasi.

Tidak diperlukan server berbayar untuk preview/pemrosesan saat aplikasi digunakan. GitHub Actions hanya digunakan saat *build*. Proses decoding, animasi, serta rendering berat tetap mengonsumsi sumber daya HP; thermal throttling masih mungkin terjadi.

## Smoke tests pure Java (tanpa Android SDK)

```bash
mkdir -p out
javac -d out app/src/main/java/com/ranneki/visualizer/{Fft,BandSpectrum,ReactiveEnvelope}.java tests/{SmokeTest,AudioReactiveTest}.java
java -cp out com.ranneki.visualizer.SmokeTest
java -cp out com.ranneki.visualizer.AudioReactiveTest
```

**Status:** source code V1.2 dalam pengembangan. Lulus tes matematika FFT dan envelope pada JVM; kompatibilitas UI/Android dan APK final tetap harus diverifikasi melalui Android build dan perangkat.
