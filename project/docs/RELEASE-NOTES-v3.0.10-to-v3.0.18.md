# Floating DJ Visualizer — Release Notes V3.0.10–V3.0.18

**Current development version:** V3.0.18  
**Android versionCode:** 95  
**Platform:** Android · Kotlin · CodeAssist

> **Language / Bahasa:** This release summary is provided in English and Bahasa Indonesia. / Ringkasan rilis ini tersedia dalam Bahasa Inggris dan Bahasa Indonesia.

## Release overview / Ringkasan rilis

| Version | English | Bahasa Indonesia |
|---|---|---|
| **V3.0.10** | Output-safety refinement for the experimental EQ output-mix route. Preamp writes were disabled where session-0 behavior could be unsafe; stored values were retained. | Penyempurnaan keamanan output pada jalur EQ output-mix eksperimental. Penulisan Preamp dinonaktifkan saat perilaku session-0 berisiko; nilai tersimpan tetap dipertahankan. |
| **V3.0.11** | Refined EQ effect controls, Floating Profile drag auto-scroll and glass scroll feedback; improved responsive Display Live layout and glow defaults. | Merapikan kontrol efek EQ, auto-scroll saat menggeser Floating Profile, dan indikator scroll glass; memperbaiki layout Display Live responsif dan nilai awal glow. |
| **V3.0.12** | Reorganized effect groups, clarified restricted Preamp/Reverb behavior, and refined play/pause controls plus screen and preset motion. | Menata ulang kelompok efek, memperjelas pembatasan Preamp/Reverb, serta menyempurnakan kontrol play/pause dan animasi layar maupun preset. |
| **V3.0.13** | Added a visible Play/Pause glyph inside theme-aware switches and a staggered preset dropdown reveal. | Menambahkan ikon Play/Pause yang terlihat di dalam switch bertema serta animasi dropdown preset bertahap. |
| **V3.0.14** | Corrected packed FFT decoding, improved frequency-range selection and transient responsiveness, coalesced stale frames, added diagnostics, refined Theme Studio color handling and notification cold-start restoration. | Memperbaiki decoding FFT, pemilihan rentang frekuensi dan respons transient, menggabungkan frame lama, menambahkan diagnostik, merapikan pengelolaan warna Theme Studio dan pemulihan notifikasi saat cold-start. |
| **V3.0.15** | Fixed numeric editor compilation and added reversible live slider previews. Theme repaint issues, bar-appearance controls and scene Save To were refined. | Memperbaiki kompilasi editor angka dan menambahkan preview slider real-time yang dapat dibatalkan. Repaint tema, kontrol tampilan bar, dan Scene Save To turut dirapikan. |
| **V3.0.16** | Introduced per-slider multi-layer linking with multi-select, mode-aware eligibility, global FFT/Hop explanations, synchronized Start/Stop controls and notification Theme Studio contrast. | Memperkenalkan Linked multi-layer per slider dengan multi-select, validasi mode, penjelasan FFT/Hop global, sinkronisasi Start/Stop, dan kontras notifikasi yang mengikuti Theme Studio. |
| **V3.0.17** | Added safer general backup/import validation, source-state review without transferring runtime permissions, improved scene and position UI, mode-safe links, and audio-engine architecture notes. | Menambahkan validasi backup/impor umum yang lebih aman, ringkasan status sumber tanpa memindahkan izin runtime, penyempurnaan UI scene dan posisi, Linked sesuai mode, serta catatan arsitektur audio engine. |
| **V3.0.18** | Expanded the 5-band/10-band starter EQ library with capability-based filtering and Band Analyzer; refined scroll gutters, Display geometry, Theme Studio toasts, dialog/list motion and responsive spacing. | Memperluas preset EQ 5-band/10-band dengan penyaringan berdasarkan kemampuan backend dan Band Analyzer; merapikan gutter scroll, geometri Display, toast Theme Studio, animasi dialog/daftar, dan jarak UI responsif. |

## V3.0.18 maintenance correction — Equalizer first-open scroll rail

### English

- Reworked the Equalizer Activity page indicator as a viewport-sized sibling overlay, matching the main Visualizer page's right-edge glass rail pattern.
- Removed the early `windowVisibility` gate that could hide the rail during Activity launch/transition and leave it hidden until a later theme or engine refresh.
- Reserved a clear right-side content gutter so the rail sits outside EQ cards instead of overlapping or touching their contents.
- Refreshes the indicator on layout, Activity resume, window focus and scroll changes. It is independent of EQ ON/OFF state.
- Kept `versionName = 3.0.18` and `versionCode = 95`. No EQ DSP, audio routing, FFT algorithm, preset data or saved profile values are changed by this correction.

### Bahasa Indonesia

- Mengubah indikator halaman Equalizer menjadi overlay sibling setinggi viewport, mengikuti pola rail glass di sisi kanan halaman utama Visualizer.
- Menghapus pemeriksaan `windowVisibility` terlalu awal yang dapat menyembunyikan rail saat Activity dibuka/bertransisi dan membuatnya tetap tersembunyi sampai tema atau engine diperbarui.
- Menyediakan gutter kanan yang jelas agar rail berada di luar kartu EQ, bukan menumpuk atau menyentuh isi kartu.
- Memperbarui indikator saat layout berubah, Activity kembali aktif, window mendapat fokus, dan halaman digulir. Indikator tidak bergantung pada status EQ ON/OFF.
- Mempertahankan `versionName = 3.0.18` dan `versionCode = 95`. Koreksi ini tidak mengubah EQ DSP, routing audio, algoritma FFT, data preset, atau nilai profile yang tersimpan.

## Audio-processing note / Catatan pemrosesan audio

**EN:** The EQ engine remains experimental and device-dependent. The current output-mix route is not a universal per-player or system-wide mixer. Effect availability, band count and control behavior may vary by Android version, OEM implementation and active audio route. Preamp remains restricted on the current output-mix path for output safety.

**ID:** EQ engine masih eksperimental dan bergantung pada perangkat. Jalur output-mix saat ini bukan mixer universal per-player atau seluruh sistem. Ketersediaan efek, jumlah band, dan perilaku kontrol dapat berbeda menurut versi Android, implementasi OEM, dan jalur audio aktif. Preamp tetap dibatasi pada jalur output-mix saat ini demi keamanan output.

## Compatibility / Kompatibilitas

- Minimum Android: API 26 / Android 8.0.
- Target SDK: API 36.
- Floating Overlay requires Android's user-granted overlay permission.
- EQ features depend on the effects and audio route available on the device.

## Verification note / Catatan verifikasi

This package's source/resource checks and archive-integrity checks are performed separately from a device build. Final APK compilation and runtime behavior must be verified in CodeAssist on the target device. / Pemeriksaan source/resource dan integritas arsip dilakukan terpisah dari build perangkat. Kompilasi APK dan perilaku runtime tetap harus diverifikasi melalui CodeAssist pada perangkat target.
