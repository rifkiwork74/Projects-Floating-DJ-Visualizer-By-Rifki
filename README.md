<div align="center">

# Floating DJ Visualizer

### Android Floating Audio Visualizer &amp; Experimental Equalizer

**Developed by Rifki**

<p>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&amp;logo=android&amp;logoColor=white" alt="Platform: Android">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&amp;logo=kotlin&amp;logoColor=white" alt="Language: Kotlin">
  <img src="https://img.shields.io/badge/Version-v3.0.18-2563EB?style=for-the-badge" alt="Version 3.0.18">
  <a href="https://github.com/tyron12233/CodeAssist">
    <img src="https://img.shields.io/badge/IDE-CodeAssist-24292F?style=for-the-badge&amp;logo=github&amp;logoColor=white" alt="Built with CodeAssist">
  </a>
</p>

**Visualize your sound. Customize every layer.**

<p>
  <strong>&#127468;&#127463; EN</strong> - A customizable, audio-reactive floating visualizer with independent layers, reusable scenes, Theme Studio, and an experimental device-dependent Equalizer Engine.
  <br>
  <strong>&#127470;&#127465; ID</strong> - Floating visualizer responsif yang dapat dikustomisasi dengan layer independen, scene yang dapat digunakan kembali, Theme Studio, dan Equalizer Engine eksperimental yang bergantung pada perangkat.
</p>

<p>
  <a href="#app-preview">Preview</a> &middot;
  <a href="#key-features">Features</a> &middot;
  <a href="#audio-processing-notes">Audio Notes</a> &middot;
  <a href="#compatibility">Compatibility</a> &middot;
  <a href="#changelog">Changelog</a> &middot;
  <a href="#contact">Contact</a>
</p>

</div>

---

## Contents | Daftar Isi

- [App Preview | Pratinjau Aplikasi](#app-preview)
- [About | Tentang Proyek](#about)
- [Key Features | Fitur Utama](#key-features)
- [Test Presets | Preset Uji Coba](#test-presets)
- [Technology Stack | Teknologi](#technology-stack)
- [Compatibility | Kompatibilitas](#compatibility)
- [Audio Processing Notes | Catatan Audio](#audio-processing-notes)
- [Development History & Changelog](#changelog)
- [Contact | Kontak](#contact)

---

## App Preview

*Pratinjau Aplikasi*

<details open>
<summary><strong>&#127468;&#127463; Open screenshot gallery / &#127470;&#127465; Buka galeri (6 images)</strong></summary>

<p align="center"><sub>Tap a thumbnail to view the full-size image. / Ketuk thumbnail untuk melihat gambar ukuran penuh.</sub></p>

<div align="center">
<table align="center">
  <tr>
    <td align="center" width="33%">
      <a href="dokumentasi/dokumentasi-1.jpg"><img src="dokumentasi/dokumentasi-1.jpg" width="118" alt="Main application dashboard"></a><br>
      <sub><strong>01 - Main Dashboard</strong><br>Halaman Utama</sub>
    </td>
    <td align="center" width="33%">
      <a href="dokumentasi/dokumentasi-2.jpg"><img src="dokumentasi/dokumentasi-2.jpg" width="118" alt="Floating profiles and layers"></a><br>
      <sub><strong>02 - Floating Profiles</strong><br>Profile dan Layer</sub>
    </td>
    <td align="center" width="33%">
      <a href="dokumentasi/dokumentasi-3.jpg"><img src="dokumentasi/dokumentasi-3.jpg" width="118" alt="Palette Studio with bar color profiles"></a><br>
      <sub><strong>03 - Bar Palette Studio</strong><br>Studio Palet Warna Bar</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <a href="dokumentasi/dokumentasi-4.jpg"><img src="dokumentasi/dokumentasi-4.jpg" width="118" alt="Equalizer bands interface"></a><br>
      <sub><strong>04 - Equalizer Bands</strong><br>Band Equalizer</sub>
    </td>
    <td align="center" width="33%">
      <a href="dokumentasi/dokumentasi-5.jpg"><img src="dokumentasi/dokumentasi-5.jpg" width="118" alt="Equalizer components and audio effects"></a><br>
      <sub><strong>05 - Equalizer Components</strong><br>Komponen Equalizer dan Efek Audio</sub>
    </td>
    <td align="center" width="33%">
      <a href="dokumentasi/dokumentasi-6.jpg"><img src="dokumentasi/dokumentasi-6.jpg" width="118" alt="100-color bar palette gallery"></a><br>
      <sub><strong>06 - 100 Bar Color Palettes</strong><br>Galeri 100 Palet Warna Bar</sub>
    </td>
  </tr>
</table>
</div>

</details>

<p align="center">
  <sub>&#127468;&#127463; More screenshots are available in <a href="dokumentasi/">dokumentasi/</a>. / &#127470;&#127465; Tangkapan layar lainnya tersedia di folder <a href="dokumentasi/">dokumentasi/</a>.</sub>
</p>

---

## About | Tentang Proyek

**&#127468;&#127463; English**

Floating DJ Visualizer is an Android application developed by **Rifki**. It began as a floating audio visualizer and has evolved into a layered visual workspace for real-time audio-reactive displays. The application includes independent floating profiles, per-layer controls, reusable scenes, detailed appearance settings, and an additional experimental Equalizer Engine.

The project is developed directly on an Android device using the open-source [CodeAssist IDE](https://github.com/tyron12233/CodeAssist).

**&#127470;&#127465; Bahasa Indonesia**

Floating DJ Visualizer adalah aplikasi Android yang dikembangkan oleh **Rifki**. Berawal dari floating audio visualizer, proyek ini berkembang menjadi workspace visual berlapis yang merespons audio secara real-time. Aplikasi menyediakan floating profile independen, kontrol setiap layer, scene yang dapat digunakan kembali, pengaturan tampilan mendalam, serta Equalizer Engine tambahan yang masih bersifat eksperimental.

Proyek ini dikembangkan langsung melalui perangkat Android menggunakan [CodeAssist IDE](https://github.com/tyron12233/CodeAssist) yang bersifat open-source.

> **Project status / Status proyek:** V3.0.18 is the current documented development line. The Equalizer Engine is experimental and device-dependent. / V3.0.18 adalah lini pengembangan yang didokumentasikan saat ini. Equalizer Engine masih eksperimental dan bergantung pada perangkat.

---

## Key Features | Fitur Utama

### 1. Audio Visualization | Visualisasi Audio

| Feature | &#127468;&#127463; English | &#127470;&#127465; Bahasa Indonesia |
|---|---|---|
| **Floating Overlay** | Displays the visualizer above other apps after overlay permission is granted. | Menampilkan visualizer di atas aplikasi lain setelah izin overlay diberikan. |
| **Visualizer Modes** | Supports Single and Horizontal / DJ layouts. | Mendukung layout Single dan Horizontal / DJ. |
| **Bar Controls** | Adjust bar count, width, height, thickness, gap, scale, opacity, glow, and corner shape where supported. | Mengatur jumlah bar, lebar, tinggi, ketebalan, jarak, skala, opacity, glow, dan bentuk sudut sesuai mode. |
| **Frequency Display** | Shows frequency ranges and activity levels for the selected mode. | Menampilkan rentang frekuensi dan level aktivitas sesuai mode yang dipilih. |
| **Appearance Styles** | Clean, Glossy, Neon, and Frosted styles with editable color profiles. | Gaya Clean, Glossy, Neon, dan Frosted dengan profil warna yang dapat diedit. |
| **Position Studio** | Controls the position, movement, and rotation of each floating layer. | Mengatur posisi, pergerakan, dan rotasi setiap floating layer. |

### 2. Visual Studio &amp; Workflow | Studio Visual &amp; Alur Kerja

| Feature | &#127468;&#127463; English | &#127470;&#127465; Bahasa Indonesia |
|---|---|---|
| **Theme Studio** | Glossy, Custom, System, Dark, and Light interface themes. | Tema antarmuka Glossy, Custom, System, Dark, dan Light. |
| **Floating Profiles &amp; Layers** | Manage multiple independent layers and change their order. | Mengelola beberapa layer independen dan mengubah urutannya. |
| **Per-Slider Linking** | Link one supported parameter to multiple compatible layers, with mode-aware target selection. | Menghubungkan satu parameter ke beberapa layer yang kompatibel dengan pemeriksaan mode. |
| **Copy To** | Copy supported profile or appearance settings to another layer. | Menyalin pengaturan profile atau tampilan yang didukung ke layer lain. |
| **Scene Manager** | Save, load, update, rename, delete, import, and export floating scenes. | Menyimpan, memuat, memperbarui, mengganti nama, menghapus, mengimpor, dan mengekspor scene. |
| **Live Numeric Preview** | Preview slider and numeric changes, then commit or cancel them. | Melihat pratinjau perubahan slider dan angka, lalu menyimpan atau membatalkannya. |
| **General Import / Export** | Back up supported settings, profiles, scenes, and EQ data with validation and import review. Runtime permissions and active engines are not transferred or auto-started. | Mencadangkan pengaturan, profile, scene, dan data EQ yang didukung melalui validasi dan tinjauan impor. Izin runtime serta engine aktif tidak dipindahkan atau dijalankan otomatis. |
| **Notification Controls** | Theme-aware controls for supported visualizer and EQ actions. | Kontrol notifikasi yang mengikuti tema untuk fungsi visualizer dan EQ yang didukung. |

### 3. Experimental Equalizer Engine | Equalizer Eksperimental

| Feature | &#127468;&#127463; English | &#127470;&#127465; Bahasa Indonesia |
|---|---|---|
| **Adaptive EQ Bands** | Shows supported band layouts detected by the active backend, including 5-band or 10-band configurations where available. | Menampilkan susunan band yang terdeteksi pada backend aktif, termasuk konfigurasi 5-band atau 10-band jika tersedia. |
| **Band Analyzer &amp; Presets** | Reports detected band capabilities and filters built-in presets to matching band counts. | Menampilkan kemampuan band yang terdeteksi dan menyaring preset bawaan berdasarkan jumlah band yang sesuai. |
| **Band Editing** | Vertical faders, direct dB input, and individual band reset. | Fader vertikal, input dB langsung, dan reset setiap band. |
| **Additional Effects** | Bass Boost, Virtualizer, and Limiter controls where supported by the device and backend. | Kontrol Bass Boost, Virtualizer, dan Limiter sesuai dukungan perangkat dan backend. |
| **EQ Preset Import / Export** | Import and export supported EQ preset files. | Mengimpor dan mengekspor file preset EQ yang didukung. |

---

## Test Presets | Preset Uji Coba

**&#127468;&#127463; English**

Example configurations from development and testing may be available in [`Test - Preset/`](Test%20-%20Preset/). Use files compatible with your application version and review their contents before applying them.

**&#127470;&#127465; Bahasa Indonesia**

Contoh konfigurasi dari proses pengembangan dan pengujian dapat tersedia di folder [`Test - Preset/`](Test%20-%20Preset/). Gunakan file yang sesuai dengan versi aplikasi dan periksa isinya sebelum diterapkan.

---

## Technology Stack | Teknologi

| Component | Technology |
|---|---|
| Platform | Android |
| Main language | Kotlin |
| Development IDE | [CodeAssist](https://github.com/tyron12233/CodeAssist) |
| UI framework | Android Views |
| Audio visualization | Android `Visualizer` API and optional MediaProjection PCM analysis |
| Equalizer | Android audio effects and `DynamicsProcessing` where supported |
| Build tools | Gradle and Android Build Tools |

---

## Compatibility | Kompatibilitas

| Item | Configuration |
|---|---|
| Minimum Android | Android 8.0 (API 26) |
| Target SDK | API 36 |
| Device | Android phones and compatible devices |
| Floating Overlay | Requires the user's Android "Display over other apps" permission. / Memerlukan izin Android "Tampilkan di atas aplikasi lain". |
| Audio effects | Availability and control vary by device, ROM, player, and active audio route. / Ketersediaan dan kontrol bergantung pada perangkat, ROM, player, dan jalur audio aktif. |

---

## Audio Processing Notes | Catatan Pemrosesan Audio

**&#127468;&#127463; English**

The Equalizer Engine is experimental and device-dependent. The current Android output-mix route is not a universal per-player or system-wide mixer. Effect availability, band count, and control behavior may vary by Android version, manufacturer implementation, and active audio route. Detecting an effect does not guarantee that every application or audio route is processed. Some effects require a compatible player-owned audio session.

**&#127470;&#127465; Bahasa Indonesia**

Equalizer Engine masih eksperimental dan bergantung pada perangkat. Jalur output-mix Android saat ini bukan mixer universal per-player atau mixer seluruh sistem. Ketersediaan efek, jumlah band, dan perilaku kontrol dapat berbeda menurut versi Android, implementasi produsen, serta jalur audio aktif. Efek yang terdeteksi tidak menjamin seluruh aplikasi atau jalur audio ikut diproses. Beberapa efek memerlukan audio session milik player yang kompatibel.

---

<a id="changelog"></a>

## Development History & Changelog | Riwayat Pengembangan

This section summarizes the project's documented evolution from its original V1 foundation through V2.4.32 and the Version 3 development line. Older milestones are grouped where individual patch-level records are unavailable. For the latest implementation details, review the repository commit history and published GitHub Releases.

Bagian ini merangkum perjalanan proyek sejak fondasi awal V1, berlanjut ke V2.4.32, hingga lini pengembangan Versi 3. Riwayat lama dikelompokkan apabila catatan setiap patch tidak tersedia. Untuk rincian implementasi terbaru, lihat riwayat commit repository dan GitHub Releases.

<p align="center">
  <a href="https://github.com/rifkiwork74/Projects-Floating-DJ-Visualizer-By-Rifki/commits/main">
    <img src="https://img.shields.io/badge/GitHub-Commit%20History-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Commit History">
  </a>
  <a href="https://github.com/rifkiwork74/Projects-Floating-DJ-Visualizer-By-Rifki/releases">
    <img src="https://img.shields.io/badge/GitHub-Releases-2F81F7?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Releases">
  </a>
</p>

<details>
<summary><strong>V1.x — Original Visualizer Foundation</strong> · Initial floating audio visualization</summary>

**EN — Original foundation**

The V1 series established the core identity of Floating DJ Visualizer: an Android audio visualizer that can remain visible above other applications through a floating overlay.

- **Floating Overlay:** Display the visualizer above other applications and move it to different screen positions.
- **Single & Horizontal / DJ Modes:** Provide the initial visualization layout choices.
- **Color Customization:** Adjust visualizer colors using HEX and RGB values.
- **Bar Configuration:** Customize bar count, spacing, thickness, height, and visual appearance.
- **Live Preview:** Preview visual changes while adjusting settings.
- **Position Control:** Adjust the floating visualizer's horizontal and vertical placement.

**ID — Fondasi awal**

Seri V1 membangun identitas utama Floating DJ Visualizer: aplikasi visualisasi audio Android yang dapat tetap tampil di atas aplikasi lain melalui floating overlay.

- **Floating Overlay:** Menampilkan visualizer di atas aplikasi lain dan memindahkannya ke berbagai posisi layar.
- **Mode Single & Horizontal / DJ:** Menghadirkan pilihan layout visualisasi awal.
- **Kustomisasi Warna:** Mengatur warna visualizer menggunakan nilai HEX dan RGB.
- **Pengaturan Bar:** Mengubah jumlah bar, jarak, ketebalan, tinggi, dan tampilan visual.
- **Live Preview:** Melihat pratinjau perubahan visual saat mengatur tampilan.
- **Kontrol Posisi:** Mengatur posisi horizontal dan vertikal floating visualizer.

<blockquote>
  <strong>Historical record / Catatan riwayat:</strong> The available V1 documentation records these foundational capabilities as a grouped V1.x summary; it does not provide a reliable patch-by-patch version list. The historical README revision is preserved in GitHub commit history.
  <br>
  Dokumentasi V1 yang tersedia mencatat kemampuan dasar tersebut sebagai ringkasan seri V1.x; tidak tersedia daftar perubahan setiap patch yang dapat diverifikasi. Revisi README historis dapat dilihat melalui commit GitHub.
</blockquote>

[View the historical V1 README commit](https://github.com/rifkiwork74/Projects-Floating-DJ-Visualizer-By-Rifki/commit/4aa7b510cac972d0fe655698b98a61957ab53311).

</details>

<details>
<summary><strong>V2.4.32 — Final V2 Release</strong> · Visualizer engine and UI refinement</summary>

**EN — Final V2 milestone**

V2.4.32 marked the final documented V2 update before the project moved into the Version 3 Equalizer Engine milestone.

- **Live Engine Restored:** Restored the complete V2.4.30 Display / Choreographer preview scheduling and live interpolation. Removed the V2.4.31 preview-pause, 30 FPS pacing, and parent-only invalidation changes associated with frozen Display previews.
- **Display Glow Optimization:** Reduced the in-app Display preview glow by replacing its heavier multi-pass halo with a smaller, lower-intensity treatment. This optimization was limited to the in-app preview.
- **Floating Renderer Preserved:** Kept the full Floating Service renderer and its established visual output unchanged.
- **Renderer Integrity:** Preserved FFT delivery, 20 Hz–20 kHz frequency mapping, frame-synchronized animation, Single / Horizontal geometry, and overlay position / rotation behavior.
- **Simultaneous Rendering:** Kept the Display preview and Floating renderer operating simultaneously, without scroll-triggered preview pauses or an imposed Display frame cap.
- **Live Hz Tracking:** Updated Live Hz tracking so its frequency indicators follow the configured total bar count.
- **Real-Time Color Studio:** Expanded the color album to 100 palette presets with live preview and Apply / Cancel behavior.
- **Floating Profile Expansion:** Redesigned the Floating Profile interface to support up to 12 floating overlay profiles.
- **General UI Refinement:** Polished the interface and workflow while retaining the V2 renderer baseline.

**ID — Milestone terakhir V2**

V2.4.32 merupakan pembaruan V2 terakhir yang terdokumentasi sebelum proyek memasuki milestone Equalizer Engine pada Versi 3.

- **Pemulihan Live Engine:** Mengembalikan penjadwalan preview Display / Choreographer dan interpolasi real-time dari V2.4.30 secara penuh. Perubahan preview-pause, pacing 30 FPS, dan invalidation parent-only dari V2.4.31 yang berkaitan dengan preview macet dihapus.
- **Optimasi Glow Display:** Mengurangi glow pada preview Display di dalam aplikasi dengan efek halo yang lebih kecil dan lebih ringan. Optimasi ini hanya diterapkan pada preview dalam aplikasi.
- **Floating Renderer Dipertahankan:** Mempertahankan renderer Floating Service beserta hasil visualnya tanpa perubahan.
- **Integritas Renderer:** Mempertahankan pengiriman FFT, pemetaan frekuensi 20 Hz–20 kHz, animasi sinkron-bingkai, geometri Single / Horizontal, serta perilaku posisi dan rotasi overlay.
- **Rendering Simultan:** Menjaga preview Display dan renderer Floating berjalan bersamaan tanpa jeda akibat scrolling atau pembatasan frame khusus pada Display.
- **Live Hz Tracking:** Memperbarui indikator Live Hz agar mengikuti total jumlah bar yang dikonfigurasi.
- **Real-Time Color Studio:** Mengembangkan album warna menjadi 100 preset palet dengan live preview serta fungsi Apply / Cancel.
- **Ekspansi Floating Profile:** Mendesain ulang antarmuka Floating Profile agar mendukung hingga 12 floating overlay profile.
- **Penyempurnaan UI:** Merapikan antarmuka dan alur penggunaan dengan tetap mempertahankan baseline renderer V2.

<blockquote>
  <strong>Version metadata / Metadata versi:</strong> The documented V2.4.32 package used versionCode 61. The V2.4.32 summary is a consolidated milestone note, not a claim that every internal repair was published as a separate release.
  <br>
  Paket V2.4.32 yang terdokumentasi menggunakan versionCode 61. Ringkasan ini merupakan catatan milestone gabungan, bukan klaim bahwa setiap perbaikan internal diterbitkan sebagai rilis terpisah.
</blockquote>

[Browse the repository commit history](https://github.com/rifkiwork74/Projects-Floating-DJ-Visualizer-By-Rifki/commits/main).

</details>

<details>
<summary><strong>V3.0.0–V3.0.9 — Experimental Equalizer Engine Milestone</strong> · From visualizer to layered audio workspace</summary>

**EN — Version 3 foundation**

Version 3 introduced a separate, experimental Equalizer Engine while retaining the V2.4.32 visualizer renderer as its baseline.

- **Dedicated Equalizer Activity:** Added a separate EQ workspace and isolated audio-effect controller with runtime capability checks.
- **Adaptive EQ Backend:** Added a 10-band DynamicsProcessing path on API 28+ where available, with a native Equalizer fallback that reports the actual device band count.
- **Device Capability Detection:** Added runtime discovery and read-back checks for the active audio effects instead of assuming uniform device support.
- **Additional Audio Effects:** Added optional Bass Boost and Virtualizer controls where the active backend permits them.
- **Preamp & Limiter:** Added DynamicsProcessing Input Gain and an experimental Limiter stage where supported.
- **Preset Workflow:** Added working-state persistence, named preset management, reset, and EQ preset JSON import / export.
- **Theme Studio Integration:** Connected the EQ interface to the application's theme, language, and text-scale preferences.
- **Mixer Interaction:** Added precision faders, direct dB entry, individual band reset, and horizontal navigation for multi-band layouts.
- **Notification Controls:** Added supported Visualizer and EQ actions to the foreground notification.
- **Engine Safety & State Handling:** Improved effect capability checks, working-state restoration, and control feedback.

**ID — Fondasi Versi 3**

Versi 3 menghadirkan Equalizer Engine eksperimental yang terpisah, dengan tetap mempertahankan renderer visualizer V2.4.32 sebagai baseline.

- **Equalizer Activity Terpisah:** Menambahkan workspace EQ khusus dan controller audio effect terisolasi dengan pemeriksaan kapabilitas saat runtime.
- **Backend EQ Adaptif:** Menambahkan jalur DynamicsProcessing 10-band pada API 28+ jika tersedia, dengan fallback Equalizer native yang menampilkan jumlah band aktual dari perangkat.
- **Deteksi Kapabilitas Perangkat:** Menambahkan pemeriksaan effect dan read-back runtime, bukan menganggap semua perangkat memiliki dukungan yang sama.
- **Efek Audio Tambahan:** Menambahkan kontrol Bass Boost dan Virtualizer opsional sesuai kemampuan backend aktif.
- **Preamp & Limiter:** Menambahkan Input Gain DynamicsProcessing dan stage Limiter eksperimental pada perangkat yang mendukung.
- **Alur Preset:** Menambahkan penyimpanan working state, pengelolaan preset, reset, serta import / export preset EQ JSON.
- **Integrasi Theme Studio:** Menghubungkan tampilan EQ dengan preferensi tema, bahasa, dan skala teks aplikasi.
- **Interaksi Mixer:** Menambahkan fader presisi, input dB langsung, reset setiap band, dan navigasi horizontal untuk layout multi-band.
- **Kontrol Notifikasi:** Menambahkan aksi Visualizer dan EQ yang didukung pada foreground notification.
- **Keamanan Engine & State:** Menyempurnakan pemeriksaan kapabilitas effect, pemulihan working state, dan feedback kontrol.

<blockquote>
  <strong>Audio limitation / Batasan audio:</strong> The output-mix / session-0 route is experimental and device-dependent. Detecting or constructing an effect does not prove that all third-party applications or every audio route are processed. Reverb remains capability detection only in this milestone because a compatible player-owned session and auxiliary send path are required.
  <br>
  Jalur output-mix / session-0 masih eksperimental dan bergantung pada perangkat. Effect yang terdeteksi atau berhasil dibuat tidak membuktikan bahwa seluruh aplikasi pihak ketiga atau semua jalur audio ikut diproses. Pada milestone ini, Reverb hanya dideteksi kapabilitasnya karena memerlukan session milik player yang kompatibel dan jalur auxiliary send.
</blockquote>

<sub>Milestone note: the documented progression groups V3.0.0, V3.0.1–V3.0.3, V3.0.5, V3.0.6–V3.0.7, V3.0.8–V3.0.8.1, and V3.0.9. A separate V3.0.4 change record was not available in the implementation dossier.</sub>

<sub>Catatan milestone: progres yang terdokumentasi mengelompokkan V3.0.0, V3.0.1–V3.0.3, V3.0.5, V3.0.6–V3.0.7, V3.0.8–V3.0.8.1, dan V3.0.9. Catatan perubahan V3.0.4 secara terpisah tidak tersedia dalam dossier implementasi.</sub>

</details>

<details>
<summary><strong>V3.0.18</strong> - Display geometry, scroll rails, EQ presets, and UI polish</summary>

- **EN:** Expanded the built-in EQ preset library for detected 5-band and 10-band layouts. Refined Band Analyzer filtering, Live Display geometry, first-open Equalizer rail visibility, right-side scroll gutters, theme-aware toast messages, and interface spacing / animations. Preserved versionName 3.0.18 and versionCode 95.
- **ID:** Memperluas pustaka preset EQ untuk susunan 5-band dan 10-band yang terdeteksi. Merapikan penyaringan Band Analyzer, geometri Live Display, visibilitas rail Equalizer saat pertama dibuka, gutter scrolling sisi kanan, toast sesuai tema, serta jarak dan animasi antarmuka. Mempertahankan versionName 3.0.18 dan versionCode 95.

</details>

<details>
<summary><strong>V3.0.17</strong> - Import validation and workflow refinements</summary>

- **EN:** Improved general backup / import validation and runtime-permission review. Refined scene status, layer-position controls, mode-safe parameter linking, and the Equalizer page scroll indicator.
- **ID:** Memperkuat validasi backup / impor umum dan pemeriksaan izin runtime. Merapikan status scene, kontrol posisi layer, Linked parameter yang sesuai mode, serta indikator scrolling halaman Equalizer.

</details>

<details>
<summary><strong>V3.0.16</strong> - Multi-layer linking and notification consistency</summary>

- **EN:** Introduced per-slider linking to multiple compatible layers, global FFT / Hop explanations, synchronized Start / Stop notification controls, and notification colors that follow Theme Studio contrast settings.
- **ID:** Memperkenalkan Linked per-slider ke beberapa layer kompatibel, penjelasan FFT / Hop global, sinkronisasi kontrol Start / Stop notifikasi, serta warna notifikasi yang mengikuti pengaturan kontras Theme Studio.

</details>

<details>
<summary><strong>V3.0.15</strong> - Reversible numeric preview and scene workflow</summary>

- **EN:** Added reversible live numeric previews, corrected Theme Studio repaint behavior, refined numeric dialogs and bar-appearance contrast, and added Scene Save To.
- **ID:** Menambahkan preview angka real-time yang dapat dibatalkan, memperbaiki repaint Theme Studio, merapikan dialog numerik dan kontras tampilan bar, serta menambahkan Scene Save To.

</details>

<details>
<summary><strong>V3.0.14</strong> - FFT responsiveness and notification restoration</summary>

- **EN:** Refined FFT decoding and responsiveness, frequency-range selection, diagnostics, Theme Studio color handling, numeric effect dialogs, and notification cold-start restoration.
- **ID:** Merapikan decoding FFT dan responsivitas, pemilihan rentang frekuensi, diagnostik, pengelolaan warna Theme Studio, dialog angka efek, serta pemulihan notifikasi saat cold-start.

</details>

<details>
<summary><strong>V3.0.13</strong> - Theme-aware switches and preset animation</summary>

- **EN:** Added visible Play / Pause glyphs to theme-aware switches and refined the EQ preset dropdown reveal and row animation.
- **ID:** Menambahkan ikon Play / Pause pada switch yang mengikuti tema serta merapikan animasi dropdown dan baris preset EQ.

</details>

<details>
<summary><strong>V3.0.12</strong> - EQ effect grouping and interaction polish</summary>

- **EN:** Reorganized EQ effect groups, clarified restricted Preamp / Reverb behavior, and refined theme-aware controls and screen motion.
- **ID:** Menata ulang kelompok efek EQ, memperjelas batasan Preamp / Reverb, serta merapikan kontrol bertema dan animasi layar.

</details>

<details>
<summary><strong>V3.0.11</strong> - EQ controls, profile auto-scroll, and Display fitting</summary>

- **EN:** Refined EQ effect controls, added Floating Profile drag auto-scroll and glass scroll feedback, and improved Display Live fitting.
- **ID:** Merapikan kontrol efek EQ, menambahkan auto-scroll saat Floating Profile digeser dan indikator scroll glass, serta meningkatkan penyesuaian Display Live.

</details>

<details>
<summary><strong>V3.0.10</strong> - Output safety and control refinements</summary>

- **EN:** Added fail-closed output safety for the experimental EQ output-mix Preamp route, refined effect switches, and improved profile scrolling while retaining saved values safely.
- **ID:** Menambahkan perlindungan output fail-closed pada jalur Preamp output-mix EQ eksperimental, merapikan switch efek, dan meningkatkan scrolling profile dengan tetap mempertahankan nilai tersimpan secara aman.

</details>

**Release and commit references / Referensi rilis dan commit**

- [Browse all repository commits](https://github.com/rifkiwork74/Projects-Floating-DJ-Visualizer-By-Rifki/commits/main)
- [Browse GitHub Releases](https://github.com/rifkiwork74/Projects-Floating-DJ-Visualizer-By-Rifki/releases)
- [View the V1 README history](https://github.com/rifkiwork74/Projects-Floating-DJ-Visualizer-By-Rifki/commit/4aa7b510cac972d0fe655698b98a61957ab53311)

<sub>Historical accuracy note: V1.x and V2.4.32 are presented as documented milestones. Where the surviving records do not identify a precise commit or separate public release, this README links to the repository-wide commit history rather than inventing a version-specific link.</sub>

<sub>Catatan akurasi riwayat: V1.x dan V2.4.32 ditampilkan sebagai milestone yang terdokumentasi. Jika arsip yang tersedia tidak menunjukkan commit atau rilis publik tertentu secara pasti, README ini mengarahkan pembaca ke riwayat commit repository secara umum dan tidak membuat tautan versi yang belum terverifikasi.</sub>

---

## Contact | Kontak

**&#127468;&#127463; English:** Feedback, bug reports, suggestions, and collaboration inquiries are welcome.

**&#127470;&#127465; Indonesia:** Masukan, laporan bug, saran, dan pertanyaan kolaborasi dipersilakan.

<p align="center">
  <a href="mailto:rifki.work74@outlook.com">
    <img src="https://img.shields.io/badge/Email-Contact%20Me-0078D4?style=for-the-badge&amp;logo=microsoftoutlook&amp;logoColor=white" alt="Contact by email">
  </a>
</p>

<p align="center">
  <strong>Made with passion by Rifki</strong><br>
  <em>Floating DJ Visualizer - Visualize Your Sound.</em>
</p>
