<div align="center">

  <h1>
    <img src="https://img.icons8.com/ios-filled/50/2196F3/headphones.png" width="32" alt="Headphone" />
    Floating DJ Visualizer
  </h1>

  <h3>Android Audio Visualizer & Experimental Equalizer · By Rifki</h3>

  <p><i>Visualize your sound. Customize every layer.</i></p>

  <p>
    <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android">
    <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
    <img src="https://img.shields.io/badge/Version-v3.0.18-2196F3?style=for-the-badge" alt="Version">
    <a href="https://github.com/tyron12233/CodeAssist" target="_blank" rel="noreferrer">
      <img src="https://img.shields.io/badge/Built%20With-CodeAssist-292929?style=for-the-badge&logo=github&logoColor=white" alt="Built with CodeAssist">
    </a>
  </p>

  <p><b>EN:</b> A customizable Android floating audio visualizer with layered profiles, scene management, Theme Studio, and an experimental device-dependent Equalizer Engine.</p>
  <p><b>ID:</b> Visualizer audio floating Android yang dapat dikustomisasi dengan multi-layer profile, pengelolaan scene, Theme Studio, dan Equalizer Engine eksperimental yang bergantung pada perangkat.</p>

  <p>
    <a href="#preview-aplikasi--app-preview">Preview</a> ·
    <a href="#fitur-utama--key-features">Features</a> ·
    <a href="#teknologi--tech-stack">Tech Stack</a> ·
    <a href="#kompatibilitas--compatibility">Compatibility</a> ·
    <a href="#riwayat-perubahan--changelog">Changelog</a> ·
    <a href="#kontak--contact">Contact</a>
  </p>

</div>

---

## 📑 Daftar Isi | Table of Contents

- [Preview Aplikasi | App Preview](#preview-aplikasi--app-preview)
- [Tentang Proyek | About](#tentang-proyek--about)
- [Fitur Utama | Key Features](#fitur-utama--key-features)
- [Preset Uji Coba | Test Presets](#preset-uji-coba--test-presets)
- [Teknologi | Tech Stack](#teknologi--tech-stack)
- [Kompatibilitas | Compatibility](#kompatibilitas--compatibility)
- [Catatan Audio | Audio Notes](#catatan-audio--audio-notes)
- [Riwayat Perubahan | Changelog](#riwayat-perubahan--changelog)
- [Kontak | Contact](#kontak--contact)

---

<a id="preview-aplikasi--app-preview"></a>

## 📸 Preview Aplikasi | App Preview

<table align="center">
  <tr>
    <td align="center"><img src="dokumentasi/dokumentasi-1.jpg" width="220" alt="Main visualizer preview" /></td>
    <td align="center"><img src="dokumentasi/dokumentasi-2.jpg" width="220" alt="Floating profile preview" /></td>
    <td align="center"><img src="dokumentasi/dikumentasi-3.jpg" width="220" alt="Theme Studio preview" /></td>
  </tr>
  <tr>
    <td align="center"><img src="dokumentasi/dokumentasi-4.jpg" width="220" alt="Scene manager preview" /></td>
    <td align="center"><img src="dokumentasi/dokumentasi-5.jpg" width="220" alt="Equalizer preview" /></td>
    <td align="center"><img src="dokumentasi/dokumentasi-6.jpg" width="220" alt="Audio controls preview" /></td>
  </tr>
</table>

<p align="center"><i>EN: More screenshots are available in <a href="dokumentasi/">dokumentasi/</a>. · ID: Kumpulan tangkapan layar lainnya tersedia di folder <a href="dokumentasi/">dokumentasi/</a>.</i></p>

---

<a id="tentang-proyek--about"></a>

## 📖 Tentang Proyek | About the Project

**EN** — Floating DJ Visualizer is an Android application developed by **Rifki**. It began as a floating audio visualizer and has grown into a layered visual workspace with real-time controls, reusable scenes, detailed appearance settings, and an additional experimental Equalizer Engine. The project is developed directly on an Android device using the open-source [CodeAssist IDE](https://github.com/tyron12233/CodeAssist).

**ID** — Floating DJ Visualizer adalah aplikasi Android yang dikembangkan oleh **Rifki**. Berawal dari floating audio visualizer, proyek ini berkembang menjadi workspace visual multi-layer dengan kontrol real-time, scene yang dapat digunakan kembali, pengaturan tampilan mendalam, dan Equalizer Engine eksperimental. Pengembangan dilakukan langsung melalui perangkat Android menggunakan IDE open-source [CodeAssist](https://github.com/tyron12233/CodeAssist).

> **Project status / Status proyek:** V3.0.18 is the current development line. The Equalizer Engine remains experimental and device-dependent. / V3.0.18 adalah lini pengembangan saat ini. Equalizer Engine masih bersifat eksperimental dan bergantung pada perangkat.

---

<a id="fitur-utama--key-features"></a>

## ✨ Fitur Utama | Key Features

### 🎵 Audio Visualization

| Feature | English | Bahasa Indonesia |
|---|---|---|
| **Floating Overlay** | Displays the visualizer above other apps when overlay permission is granted. | Menampilkan visualizer di atas aplikasi lain setelah izin overlay diberikan. |
| **Visualizer Modes** | Supports Single and Horizontal / DJ layouts. | Mendukung layout Single dan Horizontal / DJ. |
| **Bar Controls** | Adjust bar count, width, height, thickness, gap, scale, opacity, glow, and corner shape where applicable. | Mengatur jumlah bar, lebar, tinggi, ketebalan, jarak, skala, opacity, glow, dan sudut bar sesuai mode. |
| **Live Frequency** | Displays frequency ranges and levels for the selected visualization mode. | Menampilkan rentang frekuensi dan level sesuai mode visualizer yang dipilih. |
| **Appearance** | Clean, Glossy, Neon, and Frosted styles with editable color profiles. | Gaya Clean, Glossy, Neon, dan Frosted dengan color profile yang dapat diedit. |
| **Position Studio** | Configure overlay position, movement, and rotation per layer. | Mengatur posisi, pergerakan, dan rotasi overlay pada setiap layer. |

### 🎨 Visual Studio & Workflow

| Feature | English | Bahasa Indonesia |
|---|---|---|
| **Theme Studio** | Glossy, Custom, System, Dark, and Light interface themes. | Tema antarmuka Glossy, Custom, System, Dark, dan Light. |
| **Floating Profiles & Layers** | Keep multiple independent layer configurations and reorder them. | Menyimpan beberapa konfigurasi layer secara independen dan mengubah urutannya. |
| **Per-Slider Linking** | Link one supported parameter from a source layer to multiple compatible target layers. | Menghubungkan satu parameter dari layer sumber ke beberapa layer tujuan yang kompatibel. |
| **Copy To** | Copy the supported appearance/profile settings to another layer. | Menyalin pengaturan tampilan/profile yang didukung ke layer lain. |
| **Scene Manager** | Save, load, rename, update, delete, import, and export complete floating scenes. | Menyimpan, memuat, mengganti nama, memperbarui, menghapus, mengimpor, dan mengekspor scene floating. |
| **Reversible Value Preview** | Preview numeric slider changes live; commit or roll back before saving. | Melihat preview perubahan angka secara langsung; simpan atau batalkan sebelum commit. |
| **General Backup** | Export supported app settings and profiles with validation and an import review. Runtime permissions and engines are not transferred or auto-started. | Mengekspor pengaturan dan profile yang didukung dengan validasi dan ringkasan impor. Izin runtime dan engine tidak dipindahkan atau otomatis dijalankan. |
| **Notification Controls** | Theme-aware notification controls for supported visualizer and EQ actions. | Kontrol notifikasi yang mengikuti tema untuk fungsi visualizer dan EQ yang didukung. |

### 🎚️ Experimental Equalizer Engine

| Feature | English | Bahasa Indonesia |
|---|---|---|
| **5 / 10-Band EQ** | Band controls follow the active backend's detected band layout. | Kontrol band mengikuti susunan band yang terdeteksi pada backend aktif. |
| **Band Analyzer** | Reports the active/detected band count; preset lists are filtered by matching band count. | Menampilkan jumlah band aktif/terdeteksi; daftar preset disaring berdasarkan jumlah band yang sesuai. |
| **EQ Presets** | Includes starter 5-band and 10-band curves, plus user-managed presets. | Menyediakan kurva awal 5-band dan 10-band, serta preset buatan pengguna. |
| **Band Editing** | Vertical faders, direct dB input, and individual band reset. | Fader vertikal, input nilai dB langsung, dan reset tiap band. |
| **Additional Effects** | Bass Boost, Virtualizer, and Limiter where supported by the device/backend. | Bass Boost, Virtualizer, dan Limiter sesuai dukungan perangkat/backend. |
| **Preamp / Reverb** | Restricted or unavailable on the current output-mix route where safe attachment/control cannot be guaranteed. | Dibatasi atau tidak tersedia pada jalur output-mix saat ini jika attachment/kontrol yang aman tidak dapat dijamin. |
| **EQ Preset Exchange** | Import and export supported EQ preset files. | Mengimpor dan mengekspor file preset EQ yang didukung. |

---

<a id="preset-uji-coba--test-presets"></a>

## 🎨 Preset Uji Coba | Test Presets

**EN:** Example configurations from development and testing may be provided in [`Test - Preset/`](Test%20-%20Preset/). Import only files compatible with your app version and review the settings before applying them.

**ID:** Contoh konfigurasi dari proses pengembangan dan pengujian dapat tersedia di folder [`Test - Preset/`](Test%20-%20Preset/). Impor hanya file yang kompatibel dengan versi aplikasi dan periksa pengaturannya sebelum diterapkan.

---

<a id="teknologi--tech-stack"></a>

## 🛠️ Teknologi | Tech Stack

| Component | Technology / Teknologi |
|---|---|
| **Platform** | Android |
| **Language** | Kotlin |
| **IDE** | [CodeAssist](https://github.com/tyron12233/CodeAssist) — on-device Android IDE / IDE Android langsung di perangkat |
| **UI** | Android Views |
| **Visualization** | Android `Visualizer` API and optional MediaProjection PCM analysis / Android `Visualizer` API dan analisis PCM MediaProjection opsional |
| **EQ** | Android audio effects and `DynamicsProcessing` where supported / Audio effect Android dan `DynamicsProcessing` sesuai dukungan perangkat |
| **Build** | Gradle / Android Build Tools |

---

<a id="kompatibilitas--compatibility"></a>

## 📱 Kompatibilitas | Compatibility

| Item | Configuration / Konfigurasi |
|---|---|
| **Minimum Android** | Android 8.0 · API 26 |
| **Target SDK** | API 36 |
| **Device** | Android phones and compatible devices / Ponsel Android dan perangkat yang kompatibel |
| **Overlay** | Requires the user's Android "Display over other apps" permission / Memerlukan izin Android "Tampilkan di atas aplikasi lain" |
| **Audio Effects** | Availability and control vary by device, ROM, player, and active audio route / Ketersediaan dan kontrol bergantung pada perangkat, ROM, player, dan jalur audio aktif |

---

<a id="catatan-audio--audio-notes"></a>

## ⚠️ Catatan Audio | Audio Processing Notes

**EN:** The Equalizer Engine currently uses an experimental Android output-mix audio-effect route. Android versions, OEM audio stacks, effect ownership, and active audio routes can affect availability and behavior. A detected effect does not guarantee that every app or every audio route is processed. Some effects require a compatible player-owned audio session. The app does not claim to be a universal system-wide mixer.

**ID:** Equalizer Engine saat ini menggunakan jalur audio effect output-mix Android yang masih eksperimental. Versi Android, audio stack OEM, kepemilikan effect, dan jalur audio aktif dapat memengaruhi ketersediaan serta perilakunya. Efek yang terdeteksi tidak menjamin seluruh aplikasi atau seluruh jalur audio diproses. Beberapa efek memerlukan audio session milik player yang kompatibel. Aplikasi ini tidak mengklaim sebagai mixer universal untuk seluruh audio sistem.

Preamp is intentionally restricted on the current output-mix route for output safety. / Preamp sengaja dibatasi pada jalur output-mix saat ini demi keamanan level output.

---

<a id="riwayat-perubahan--changelog"></a>

## 📝 Riwayat Perubahan | Changelog (V3.0.10–V3.0.18)

| Version | English — Highlights | Indonesia — Ringkasan |
|---|---|---|
| **3.0.10** | Added output-safety protection for Preamp on the experimental output-mix route; refined effect switches and profile scrolling. | Menambahkan perlindungan Preamp pada jalur output-mix eksperimental; merapikan switch efek dan scrolling profile. |
| **3.0.11** | Refined EQ effect controls, added profile drag auto-scroll and a glass profile scroll indicator, and improved Display Live fit. | Merapikan kontrol efek EQ, menambahkan auto-scroll saat drag profile dan indikator glass, serta memperbaiki tampilan Display Live. |
| **3.0.12** | Reorganized EQ effects, clarified restricted controls, and polished theme-aware switches and screen motion. | Menata ulang efek EQ, memperjelas kontrol yang dibatasi, serta menyempurnakan switch bertema dan animasi layar. |
| **3.0.13** | Added visible Play/Pause switch glyphs and a staggered EQ preset dropdown animation. | Menambahkan ikon Play/Pause yang terlihat pada switch dan animasi dropdown preset EQ bertahap. |
| **3.0.14** | Corrected FFT decoding and responsiveness, improved theme palette isolation and numeric previews, and strengthened notification cold-start restoration. | Memperbaiki decoding FFT dan responsivitas, isolasi palet tema dan preview angka, serta pemulihan notifikasi saat service dimulai ulang. |
| **3.0.15** | Added reversible live numeric previews, fixed Theme Studio repaint issues, refined numeric dialogs, and added scene Save To. | Menambahkan preview angka real-time yang dapat dibatalkan, memperbaiki repaint Theme Studio, merapikan dialog angka, dan menambahkan Scene Save To. |
| **3.0.16** | Added per-slider multi-layer linking, mode-aware target validation, global FFT/Hop labels, synchronized notification Start/Stop, and theme-aware notification pills. | Menambahkan Linked multi-layer per slider, validasi mode target, label FFT/Hop global, sinkronisasi Start/Stop notifikasi, dan pill notifikasi yang mengikuti tema. |
| **3.0.17** | Improved import/export validation and runtime-permission review; refined scene status, position controls, and mode-safe linking. | Memperkuat validasi import/export dan pemeriksaan izin runtime; merapikan status scene, kontrol posisi, dan Linked berdasarkan mode. |
| **3.0.18** | Added band-aware 5/10-band EQ presets, Band Analyzer, theme-aware toasts, UI motion and scroll-gutter refinements, plus Equalizer first-open rail visibility correction. | Menambahkan preset EQ sesuai jumlah band, Band Analyzer, toast bertema, animasi dan penyempurnaan gutter scroll, serta perbaikan indikator Equalizer agar terlihat sejak pertama dibuka. |

For detailed implementation notes, see [`CHANGELOG.md`](CHANGELOG.md) and the research documents in [`docs/`](docs/). / Untuk catatan implementasi terperinci, lihat [`CHANGELOG.md`](CHANGELOG.md) dan dokumen riset di folder [`docs/`](docs/).

---

<a id="kontak--contact"></a>

## 📬 Kontak | Contact

**EN:** Feedback, bug reports, ideas, and collaboration are welcome.  
**ID:** Masukan, laporan bug, ide pengembangan, dan kolaborasi dipersilakan.

<p align="center">
  <a href="mailto:rifki.work74@outlook.com">
    <img src="https://img.shields.io/badge/Email-Contact%20Me-0078D4?style=for-the-badge&logo=microsoftoutlook&logoColor=white" alt="Contact via email">
  </a>
</p>

<p align="center"><b>Made with passion by Rifki</b> 🎧<br><i>Floating DJ Visualizer — Visualize Your Sound.</i></p>
