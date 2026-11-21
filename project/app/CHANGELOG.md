## V3.0.19.0 — Real-Time PCM Catch-Up & Faster Response (versionCode 105)
- **Prevent stale PCM backlog:** Keep MediaProjection's rolling FFT window sample-exact, but drain already-buffered complete Hop blocks before running the next FFT. When analysis briefly falls behind the 5.8 ms input cadence, intermediate windows are skipped and only the newest complete 4096-sample window is published; PCM samples are not dropped from the rolling window.
- **Faster response curve:** Replace the legacy 20 Hz-derived smoothing curve with direct time constants. At Response 41%, attack is approximately 16.6 ms and release 38.9 ms, evaluated against actual display-frame delta time.
- **Live/Floating parity:** Apply the same response curve to MainActivity Live Display and FloatingVisualizerService layer rendering so the preview does not feel slower than the overlay.
- **Keep frequency analysis intact:** Preserve stereo L/R FFT, 4096-point frequency resolution, 256-point Hop, the dedicated 256-band Single/DJ bank, the 55–62 Hz measurement path, and fixed per-route calibration. No frame-peak or content-dependent gain normalization is introduced.
- **Diagnostics:** Add periodic MediaProjection worker metrics for processed Hop batches, configured Hop interval, publish interval, current/average FFT analysis time, and whether the bounded catch-up drain limit was reached.
- **Limits:** Hop 256 at 44.1 kHz is a 5.80 ms analysis step, not a 5.80 ms guaranteed screen-to-screen latency. FFT 4096 remains a 92.9 ms window for low-frequency resolution; the UI animation is rendered at the device's display refresh cadence.
- **Version:** `versionName 3.0.19.0`, `versionCode 105`.

## V3.0.18.9 — Main Header Light Sweep & Theme Studio Sparkle (versionCode 104)
- Add a diagonal light sweep and restrained sparkle particles over the main-page `FLOATING DJ` title, based on the uploaded visual reference.
- Synchronize the shine with the existing animated title gradient using one `ValueAnimator`; Android's Choreographer schedules frames against the display refresh cadence instead of forcing a fixed frame rate.
- Derive the accent and edge colors from the active Theme Studio palette. CUSTOM uses its selected accent and edge; built-in themes retain their own palette.
- Run the effect only while the Visualizer reports ACTIVE and the Activity is in the foreground. Cancel/reset it on pause, stop, and destroy.
- Keep the drawing lightweight: small deterministic particles, simple layered strokes, no animated bitmap, no runtime blur, and no additional thread.
- No changes to audio capture, FFT/Hop analysis, EQ processing, FloatingVisualizerService, overlay rendering, or saved profile data.
- **Version:** `versionName 3.0.18.9`, `versionCode 104`.

## V3.0.18.8 — EQ Control Gating, Themed Rename Contrast & Animated Theme Title (versionCode 103)
- **Compile fix:** Correct the animated title gradient minimum-width conversion to use the existing `Int.dp()` extension (`80.dp()`), resolving the receiver-type mismatch from `dp(80)` without changing runtime behavior or the release version.

- **Save To scroll rail:** Add the same visible vertical scroll indicator used by the preset dropdown to the Save To dialog. Keep its viewport bounded by the available screen height on compact displays.
- **Rename dialog contrast:** Select the SAVE button text color by comparing dark and white text contrast against both accent-gradient stops. A bright Studio Custom accent now uses dark text instead of unreadable white text.
- **EQ OFF master gate:** Disable the preset selector, Save As (+), Save To, Rename, Delete, Flat, band faders, and editable effect panels while the EQ master engine is OFF. Disabled controls use a muted/greyed presentation. Capability-specific unsupported controls remain disabled when EQ is ON.
- **Flat confirmation:** Require a themed Yes/No confirmation before applying Flat. The message previews current non-zero band levels (up to eight entries plus a remainder count) and explains that all bands will become 0 dB.
- **Capture source wording:** Rename the main-page toggle label to “ENGINE AUDIO CAPTURE · M.PROJ” and localize it as “MESIN AUDIO CAPTURE · M.PROJ”.
- **Animated title:** Apply a horizontal multi-stop gradient derived from the active Theme Studio accent; CUSTOM additionally uses its selected edge color. The gradient is static when Visualizer is stopped/starting and sweeps left-to-right only while the Visualizer reports ACTIVE. The animator is canceled while the Activity is paused and on destroy.
- **Audio engine protection:** No FloatingVisualizerService, FFT, Hop, stereo analysis, M.Projection capture, EQ processing engine, or spectrum calibration implementation was changed.
- **Version:** `versionName 3.0.18.8`, `versionCode 103`.

## V3.0.18.7 — Current State Preset Flow, Linked Layers & Restart Restore (versionCode 102)

- **High-contrast confirmation action:** Calculate the minimum contrast of the YES button text against both Theme Studio accent-gradient stops. Light/gold custom accents use dark text; dark accents use white text.
- **Current State is an independent EQ snapshot:** Initialize a dedicated Current State record on first launch. Selecting a saved preset no longer overwrites the Current State snapshot as a side effect of `applyProfile`. When leaving Current State or edited working values for another preset, ask Yes/No; Yes saves the latest EQ bands and effect values to the dedicated Current State record before applying the selected preset, while No keeps the current working curve selected. Selecting a named preset does not silently replace Current State. Actual band/effect edits continue to update Current State.
- **Save To list parity:** Render compatible user-created save targets with the same section headings, row height, typography, rounded surface, and chevron treatment as the EQ preset dropdown. Built-in presets remain excluded and protected.
- **Stop/Start restore:** Preserve the last requested Visualizer modes across an explicit Stop. Start restores Floating only when it was previously requested and overlay permission is available; if M.Projection was ON, Android MediaProjection consent is requested again because the authorization token cannot be reused after Stop. The independent EQ service remains untouched.
- **Position & Visibility Linked:** Add a per-layer Linked control immediately before Position. Linked layers form a group for X/Y positional delta and hide/unhide only. Position moves apply the same effective delta after screen-edge clamping while preserving each layer's own offset; dynamic drag release also propagates the final delta. Canceling the Position dialog restores the linked group snapshot. Rename and Delete remain individual and never propagate.
- **Persistence:** Store the group identifier in each Floating Profile and preserve it through the profile library, service profile payload, and complete Floating Scene snapshots. New layers start unlinked; deleting/unlinking cleans up singleton group IDs.
- **Audio engine:** No audio capture, FFT, Hop, stereo analysis, M.Projection processing, or spectrum calibration code is changed from the tested V3.0.18.5 baseline.
- **Version:** `versionName 3.0.18.7`, `versionCode 102`.

## V3.0.18.6 — Equalizer UX, Themed Safety Dialogs & Portable General Backup

- Change Bass Boost and Virtualizer sliders to horizontal oval mixer-style caps; keep the EQ band faders unchanged.
- Restrict Save To targets to compatible user-created presets. Built-in 5/10-band library entries are protected from overwrite, rename, and delete.
- Add themed overwrite/delete and per-band reset confirmations. Band reset reports the current dB and target 0 dB and changes only the selected band.
- Make preset action labels single-line and autosizing on narrower/default-density displays; replace the EQ name prompt's stock buttons with a responsive app-themed dialog.
- Make GLASS and CUSTOM notification text/secondary colors follow the phone's system Light/Dark appearance while preserving explicit LIGHT/DARK modes.
- Add a themed Visualizer stop confirmation. Confirmed stop terminates only FloatingVisualizerService and removes its notification; the independent EQ service and notification remain running.
- Route notification Stop through the same confirmation screen.
- Make General Settings import accept portable JSON scalar preferences even when a newer key is not yet present on the receiving device. Continue excluding runtime/service state and permission/session grants; improve file-size/BOM handling and error diagnostics.
- Keep the V3.0.18.5 audio-engine implementation unchanged.
- **Version:** `versionName 3.0.18.6`, `versionCode 101`.

## V3.0.18.5 — Stereo Capture Session Stability & Direct-Level Recovery (versionCode 100)

- **Keep M.Projection enabled during FFT/Hop edits:** Treat FFT/Hop as analyzer-window settings. Reconfigure the analysis worker while retaining the existing MediaProjection token and AudioRecord instead of destroying and rebuilding the capture recorder for every setting change.
- **Prevent stale-reader races:** Give each PCM worker a generation ID. An invalidated reader cannot become active again when a new worker sets the shared running flag, and an old reader cannot trigger a false BACKUP_ERROR after an update.
- **Preserve the selected source on settings errors:** A reader reconfiguration warning no longer automatically stops MediaProjection and silently switches the engine to Android Visualizer(0). The UI keeps the M.Projection switch enabled and surfaces a distinct reconfiguration warning.
- **Maximum analysis buffer reservation:** Initialize the PCM recorder with enough buffer capacity for the maximum supported 4096-point FFT and 2048-frame Hop, so later analysis changes do not require a new AudioRecord.
- **Restore V3.0.18.1 Horizontal PCM fixed calibration:** Set the fixed PCM Hann-FFT Horizontal scale back to 9.0, matching the strong V3.0.18/V3.0.18.1 reference curve. This is a constant scale only; no frame-peak, whole-spectrum, or content-dependent normalization is applied. The dedicated Single/DJ 256-band bank remains at its proven V3.0.18.3 calibration and is not modified.
- **Keep modern stereo capture:** Preserve independent L/R FFTs and RMS magnitude combination after each channel FFT. Retain actual negotiated sample-rate mapping and Single/DJ per-profile Hz analysis.
- **Source behavior:** M.Projection ON continues to use the user-authorized stereo PCM playback-capture route; M.Projection OFF continues to use Android Visualizer(0). Hop applies only to PCM playback capture; standard Visualizer uses its device callback cadence.
- **Version:** `versionName 3.0.18.5`, `versionCode 100`.

---

## V3.0.18.4 — Horizontal Spectrum Response Recovery (versionCode 99)

- **Remove PCM Horizontal frame pumping:** Stop applying one frame-peak normalization gain to the entire MediaProjection PCM spectrum. Horizontal bands now use the fixed Hann-FFT calibration directly, so a loud vocal/cymbal peak cannot reduce or reshape unrelated bass/mid bands in that frame. The independent Single/DJ bank remains un-normalized and retains its V3.0.18.3 range calibration.
- **Improve Horizontal band aggregation:** Replace plain arithmetic averaging across the 64-band analysis bins with a restrained RMS-preserving floor (`max(mean, 0.75 × RMS)`). This helps focused vocal notes and low-frequency events remain visible when a rendered bar spans several analysis bands, without using a peak-only detector.
- **Tame output-mix Horizontal overactivity:** Keep Android `Visualizer(0)` NORMALIZED mode so the validated Single/DJ output-mix behavior is not globally changed. Apply a Horizontal-only exponent curve to the 64-band Visualizer data; the dedicated 256-band Single/DJ bank and its full-range meter slot retain their prior calculations.
- **Live/Floating parity:** Apply the same Horizontal aggregation math in the Live Display preview, layered Floating Scene renderer, and legacy/root Floating Overlay. Existing time-based attack/release, bar geometry, color/effect rendering, profile data, capture routing, FFT choices, and Hop settings remain unchanged.
- **Compatibility:** No new permission, microphone path, audio source, or protected-playback bypass. M.Projection OFF remains `Visualizer(0)`; M.Projection ON remains user-authorized playback capture.
- **Version:** `versionName 3.0.18.4`, `versionCode 99`.

---

## V3.0.18.3 — Single Low-Frequency Meter Recovery & Per-Profile Hz Resolution (versionCode 98)

- **Independent Single/DJ analysis bank:** Add a dedicated 256-band logarithmic spectrum bank for Single/DJ meters. Each Floating Profile now samples its own Start/End Hz range from this bank rather than averaging the coarser 64-band Horizontal map. The Live Display preview uses the same bank and range calculation as the Floating Overlay.
- **Low-frequency recovery:** Remove the full-frame peak normalization from the dedicated Single/DJ measurement path and calibrate that range independently with a bounded 30.0 scale. A loud unrelated vocal, cymbal, or mid/high transient can no longer pull down the selected low-frequency meter. Stereo channel magnitudes are still combined after each channel FFT using RMS energy.
- **Resolution-aware narrow ranges:** Include the actual FFT bin width in each spectrum frame. When a selected range such as 55–62 Hz is narrower than one real FFT bin, expand the measurement symmetrically only to that bin width. This avoids pretending that a coarse FFT can resolve a range narrower than its physical frequency resolution.
- **Transient response:** Apply a faster, time-based attack only to Single/DJ meters; retain the user Response setting and existing release curve. Horizontal bar smoothing and V3.0.18.2's vocal/mid calibration remain unchanged.
- **Compatibility:** Preserve legacy 66-value spectrum fallback, existing profile/scene data, audio permissions, capture routes, FFT choices, and Hop settings. No microphone path or protected-audio capture bypass is added.
- **Version:** `versionName 3.0.18.3`, `versionCode 98`.

---

## V3.0.18.2 — Cross-Source Audio Level Calibration & Capture Stability (versionCode 97)

- **Source parity:** Keep the same 64-band logarithmic frequency map, full-bin band integration, peak retention, and time-based response animation for both Android `Visualizer(0)` and PCM playback capture.
- **Normalized PCM level:** Match the normalized output-mix Visualizer behavior more closely by applying one bounded frame-peak gain across both PCM channels before the Hann FFT. Calibrate PCM Hann-bin magnitude to the Visualizer 8-bit magnitude scale; retain a gain ceiling so near-silence/noise is not amplified without limit.
- **Stereo preservation:** Keep separate left/right FFTs and combine their magnitudes as RMS energy after each channel transform. No pre-FFT L/R waveform downmix is introduced.
- **Exact Hop advancement:** Accumulate partial `AudioRecord.read()` results until one complete interleaved Hop block is available. The FFT window now advances by the configured Hop frame count rather than by an incidental partial read size.
- **Actual capture diagnostics:** Read `AudioRecord.sampleRate` after initialization and use it for PCM window duration, bin resolution, Hop interval, and frequency mapping. Report the negotiated channel mode and sample rate in status/log output.
- **Platform behavior clarified:** M.Projection OFF uses Android `Visualizer(0)` on the global output mix; it is not microphone input, and Hop Size is not applied on this platform callback route. Playback capture remains Android 10+ and is limited by the source app's usage/capture policy and user-consent requirements.
- **Privacy and compatibility:** No microphone capture path, hidden audio source, protected-content bypass, or permission bypass is added. Android 8–9 continue using the Visualizer route; Android 10+ may use user-authorized playback capture when permitted by the source app.
- **Version:** `versionName 3.0.18.2`, `versionCode 97`.

---

## V3.0.18.1 — Stereo Playback Capture & Spectrum Consistency (versionCode 96)

- **Stereo MediaProjection capture:** Prefer stereo PCM and run independent left/right FFTs before combining magnitudes by RMS energy. This avoids cancellation of anti-phase vocal, chorus, and stereo material caused by a pre-FFT mono downmix. Fall back to mono only when stereo capture cannot initialize, and log/report the channel mode.
- **Full-bin logarithmic analysis:** Measure every FFT bin inside each logarithmic Horizontal band using RMS energy plus peak retention, rather than probing only five arbitrary frequencies. This reduces missed mid/high harmonics between sample points.
- **Fractional Single-frequency ranges:** Measure narrow Single/DJ ranges through interpolation when the selected range is narrower than the device FFT resolution, instead of snapping to a neighbouring whole bin.
- **Visualizer capability reporting:** Use the maximum callback rate reported by `Visualizer.getMaxCaptureRate()` without an additional fixed 20 Hz cap. Log requested versus actually applied capture size, bin resolution, and callback cadence. Explicitly request normalized Visualizer scaling when supported.
- **Source-specific behavior clarified:** Response remains time-based bar-animation smoothing. Hop Size applies only to the MediaProjection PCM path; standard Android Visualizer uses its device-reported callback cadence.
- **Version:** `versionName 3.0.18.1`, `versionCode 96`.

---

## V3.0.18 — Equalizer First-Open Rail Visibility & Right-Gutter Alignment (versionCode 95)
- Rebuild the Equalizer Activity page scroll indicator as a viewport-sized sibling overlay, matching the main Visualizer page's right-edge glass rail rather than a narrow side-column child.
- Remove the `host.windowVisibility` readiness gate that could evaluate during the Activity launch transition, set the indicator to `GONE`, and leave it hidden until an unrelated theme/EQ refresh. Keep the rail attached and refresh on layout, Activity resume, window focus, and scrolling.
- Reserve a dedicated right content inset so the rail sits visibly outside EQ cards with a safe gap and is not clipped by the page viewport. Its visibility is independent of EQ ON/OFF state.
- Keep `versionName = "3.0.18"` and `versionCode = 95` unchanged. No EQ DSP, audio routing, FFT, preset repository, or saved profile values are modified.

## V3.0.18 — Equalizer Rail First-Open & Live Display Bar-Only Geometry Correction (versionCode 95)
- Fix the Equalizer Activity's detached vertical glass rail lifecycle. Its first onCreate measurement could run before the ScrollView was attached, leaving the rail GONE until a later theme or engine refresh. Re-evaluate on ScrollView attach and Activity resume, and use attached-window visibility rather than an early ancestor `isShown` result. The rail remains visible while EQ is OFF.
- Revert Live Spectrum from profile-aspect-driven whole-canvas sizing to the prior stable orientation-based monitor canvas: portrait remains compact, landscape gets a larger but bounded viewport. Changing Single/Horizontal profile dimensions no longer resizes the display canvas.
- Restore bar-only responsive layout: Horizontal bars fill equal cells across the Live Spectrum width and occupy the monitor canvas height; thickness, corner shape, gap and effect determine each bar's silhouette. Single keeps its configured thickness and uses the available monitor height. Remove the whole-overlay preview fit scale that made bars/canvas appear shrunken or stretched together.
- Keep Live Spectrum bar rendering changes isolated from Floating Overlay geometry, audio capture, FFT, EQ processing, and saved profile values. Preserve `versionName = "3.0.18"` and `versionCode = 95`.

## V3.0.18 — Responsive UI, Theme Toasts & Motion Polish Supplement (versionCode 95)
- Keep the Equalizer page's detached glass scroll rail visible even while the EQ engine is OFF or the page currently has no vertical overflow; in the no-overflow state, the thumb fills the track. Widen the right-side gutter and extend the rail track. Move the Floating Scene Manager rail farther outside the saved-scene cards, add content-to-gutter spacing, and lengthen its track.
- Make Live Spectrum Display canvas height responsive to the selected profile's rotated width/height ratio and available orientation space. Remove the previous 1x preview ceiling and use one uniform scale factor for both axes so preview geometry can enlarge without flattening or stretching bars independently. Landscape gets a taller adaptive canvas; Single stays a compact vertical-meter canvas.
- Replace Main and Equalizer activity system-toast calls with a shared Theme Studio-aware in-app toast surface. Light, Dark, System, Glossy, and Custom use their saved readable text/surface/accent palette; messages fade/scale in and out and use short, non-blocking display durations.
- Add shared window enter/exit animations to the app theme (short fade + subtle scale/vertical motion). Refine the EQ preset popup entrance and row stagger, and enable smooth scrolling with safe content padding. Increase Bar Appearance preset chip/arrow spacing and use auto-sizing text for compact widths.
- Increase vertical separation around GLOBAL AUDIO ANALYSIS, FFT Size, and Hop Size with clearer section dividers; retain the shared-engine explanation. Keep category dividers and Bar Appearance preset chips more spacious.
- Preserve the existing FFT behavior exactly: fresh-install default remains 2048; app choices remain 1024 / 2048 / 4096; MediaProjection PCM workspace remains capped at 4096; Android Visualizer still obeys the device-reported capture range. Add `docs/V3.0.18-FFT-Resolution-Research.md` as a research note only; no FFT value, default, or algorithm was changed.
- Keep `versionName = "3.0.18"` and `versionCode = 95` unchanged.

## V3.0.18 — Display Geometry Parity, Detached Scroll Gutters & Band-Aware EQ Presets (versionCode 95)
- Move the Equalizer page, Floating Scene Manager list, and EQ preset dropdown glass scroll indicators into reserved side gutters instead of drawing them over content. The preset popup now measures its full adaptive content height so section labels and analyzer information are not clipped on compact screens or larger font scales.
- Rework Live Spectrum Display geometry to share Floating Overlay's content-size, horizontal bar-width, inter-bar gap, and glow-reserve calculations. The in-app preview uses one uniform fit scale for width and height and scales geometric details together; Single and Horizontal bars no longer get flattened or horizontally stretched to fill the preview card.
- Make scene operation toasts explicit: save, load, update, rename, and delete messages include the scene name (and layer count where relevant). These messages now use the shared Theme Studio-aware toast surface.
- Expand the built-in EQ library with separate 5-band and 10-band starter curves for common tonal goals and genres. Curves are conservative starting points (generally within ±4 dB), retain existing preset IDs/data, and are added only when their stable IDs are absent. The 5-band grid is 60 / 230 / 910 / 3.6k / 14k Hz; the 10-band grid is 31 / 62 / 125 / 250 / 500 / 1k / 2k / 4k / 8k / 16k Hz.
- Add a Band Analyzer status using the active backend's real band list. The preset selector shows only profiles whose point count matches the detected active engine, groups user-created presets separately and places the newest user preset first. The last local band count is remembered; on a fresh install, the user is told to enable EQ once so the app can inspect the backend instead of guessing.
- Keep the current EQ routing and DSP processing model unchanged. This update does not claim that an Android global output effect has become a per-player mixer.
- Set `versionName = "3.0.18"` and `versionCode = 95`.

## V3.0.17 — Deep Dev, Mode-Safe Linking, Import Safety & UI Refinement (versionCode 94)
- Make per-slider Linked compatibility mode-aware in both directions. `START FREQUENCY` and `END FREQUENCY` are Single-only; `HORIZONTAL WIDTH` and `HORIZONTAL BAR COUNT` are Horizontal-only. Source controls refuse unsupported modes, target rows remain visible but disabled with a reason, Select All only selects eligible targets, and imported/stored links are sanitized against the actual source/target modes.
- Add a Theme Studio liquid-glass Yes/No confirmation before resetting the Single-mode Start/End Frequency range. No values are reset until the user confirms.
- Refine the Floating Layer Position dialog spacing: expand the directional pad and the distance to Position Mode/Rotation, enlarge arrow touch targets, and give the rotation slider a taller touch area with more separation from its numeric input and footer.
- Give Floating Scene status a semantic palette independent of Theme Studio: green for a saved scene that still matches the live configuration; red for unsaved/current changes. Keep the Current State row separate and constrain the saved scene list to a 10-row viewport with its own theme-aware glass scroll indicator.
- Add a theme-aware vertical glass/peek scroll indicator to the full Equalizer page. Clarify that the current equalizer is an experimental, device-dependent output-mix route.
- Expand General Settings backup to include EQ saved presets and working state, FFT Size, Hop Size, and active Floating Profile pointers. Validate recognized setting types, supported FFT/Hop steps, Floating Profile IDs/colors and mode-aware link mappings, Floating Scene IDs/layers, and EQ preset structure before committing. Backup review reports source Floating Overlay, Visualizer, EQ and MediaProjection requested states. Runtime engines and device grants are never auto-started or copied; after import, the user explicitly chooses whether to enable Floating Overlay and is directed to Android's overlay permission page when required.
- Keep the visible engine control consistent as Start/Stop (no Resume label). The main page and notification send explicit Start/Stop actions; stopped status wording is reflected in both controls.
- Add `docs/V3.0.17-Audio-Engine-Architecture-Review.md` describing the current output-mix EQ route, what can/cannot be concluded from the public iJoysoft feature listing, platform restrictions, and a safer roadmap for a future player-owned audio session. No unverified loudness boost or DSP-routing replacement is introduced in this release.
- Set `versionName = "3.0.17"` and `versionCode = 94`. Existing renderer, FFT decode and EQ band DSP behavior are otherwise unchanged.

## V3.0.16 — Notification Theme Contrast & Switch Pill Fix (versionCode 93)
- Make expanded and compact notification text use the exact saved Theme Studio text color for Glossy, Light, Dark, System, and Custom modes instead of a coarse light/dark approximation. Secondary status text and notification control icons follow the corresponding readable theme palette.
- Replace the API 31+ native notification Switch widgets with the same custom ImageView pill renderer used on older Android versions. This prevents Android/OEM default Switch tinting from overriding Theme Studio colors and makes the notification controls render consistently across API levels.
- Draw the notification pill track from the active theme: Light uses a white/light-neutral track with a dark thumb; Dark/System-night use a dark track with a light thumb; Glossy uses its built-in accent and glass surface; Custom uses the saved custom accent/surface. The thumb automatically selects a contrasting foreground so its ON/OFF position remains visible.
- Keep the notification action intents and state handling unchanged. Limit this patch to notification rendering; the main-page UI, audio engine, EQ, Floating renderer, profile/scene data, and Linked behavior are untouched.
- Keep `versionName = "3.0.16"` and `versionCode = 93` unchanged.

## V3.0.16 — Mode-Aware Slider Linking, Global Engine Labels & Notification Audio Sync (versionCode 93)
- Restrict `HORIZONTAL WIDTH` and `HORIZONTAL BAR COUNT` linking to Horizontal-mode profiles only. Their per-slider link buttons render disabled/gray while the source profile is Single; inside `LINKED TO →`, Single-mode target profiles remain visible but disabled with an explicit explanation. Select All counts and selects only eligible Horizontal targets.
- Replace the Linked dialog's sliding switch with custom pill-style `ON` and `OFF` choices. The dialog keeps a green ACTIVE / red OFF status, while the active slider-link icon follows the current Theme Studio accent and unavailable controls stay neutral gray.
- Add a clear GLOBAL ENGINE label and explanatory copy for FFT Size and Hop Size. These values affect the shared audio-analysis engine across all Floating Profiles and intentionally remain outside per-layer Linked settings.
- Unify MainActivity and notification audio controls through explicit, idempotent Start and Stop service actions. Repeated notification taps no longer rely on a stale toggle PendingIntent; Stop releases the current Visualizer/MediaProjection resources and Start reinitializes the primary engine using the same service engine-start path as the main-page control.
- Keep the main-page control synchronized with notification state, including an explicit Retry state when the primary Visualizer cannot initialize; the foreground host remains available for optional MediaProjection backup.
- Keep `versionName = "3.0.16"` and Android `versionCode = 93` unchanged. Existing Copy To behavior, audio DSP/FFT algorithms, EQ behavior, renderer, and saved profile schema remain compatible.

## V3.0.16 — Per-Slider Multi-Layer Linking (versionCode 93)
- Add a custom chain-link control beside every Floating Profile parameter slider. Inactive links use a neutral theme surface; active links use the current Theme Studio accent and show the selected target count.
- Add a `LINKED TO →` dialog with an explicit ACTIVE/OFF status, pill-style ON/OFF choices, Select All/Clear All action, scrollable multi-select list, and Save Link/Cancel actions. One source layer can target multiple other Floating Profile layers independently for each parameter.
- Link only the selected scalar parameter. Supported layer controls are Horizontal Width, Bar Height/Length, Horizontal Bar Count, Bar Thickness, Bar Gap, Overall Scale, Bar Opacity, Shadow Bar Opacity, Glow Intensity, Corner Roundness, Response Speed, Start Frequency, and End Frequency. Other target properties such as color stops, effect, position, visibility, and unrelated slider values remain independent.
- Slider movement and numeric editor preview send temporary linked values to the relevant live floating layer without writing those target values yet. Committing the edit writes only that parameter to configured targets; cancelling the numeric dialog drops the temporary target preview. Link mappings are persisted with each source layer and included in saved Floating Scenes.
- Keep the existing Copy To behavior unchanged. FFT Size and Hop Size intentionally do not expose Linked because they configure the shared audio-analysis engine rather than an individual Floating Profile layer. Existing profiles without `slider_links` load with linking OFF by default.
- Set `versionName = "3.0.16"` and increment Android `versionCode` from 92 to 93. Audio capture, EQ processing, FFT decoding, and rendering algorithms are not changed by this feature.

## V3.0.15 — Reversible Live Numeric Slider Preview Fix (versionCode 92)
- Make every main-page numeric value dialog preview changes in real time while its slider is dragged or a valid number is typed. The value chip, Display Live preview, and Floating renderer receive the temporary value without immediately saving it.
- Commit the preview only with PREV, NEXT, or SAVE. BACK, the close control, and system Back now restore the original value; dismissing without a commit does not write the temporary value into SharedPreferences or the selected Floating Profile.
- Snapshot and restore dependent control state as well as the edited value, including Hop Size when FFT Size changes and floating position when geometry is clamped. Discrete FFT/Hop options remain selectable as discrete slider stops.
- Keep the correction within V3.0.15: `versionName = "3.0.15"` and `versionCode = 92` remain unchanged. Audio capture, EQ processing, and rendering algorithms are not modified.

## V3.0.15 — Theme Studio Dark Repaint Fix (versionCode 92)
- Fix stale Bar Appearance preset navigation colors: the previous/next arrows and Open All Looks button now reapply the active Theme Studio palette whenever the theme changes, including DARK and SYSTEM night mode.
- Fix Floating Profile visibility-eye controls: their ImageButton background and icon tint are refreshed with the active light/dark palette instead of retaining the surface from the theme in which the row was first created.
- Preserve the current app identity exactly: `versionName = "3.0.15"` and `versionCode = 92` remain unchanged. No audio engine, FFT/EQ, floating renderer, scene data, or preset values are modified by this fix.

## V3.0.15 — Numeric Editor Compile Fix, Bar Appearance Contrast & Scene Save To (versionCode 92)
- Align MainActivity numeric value dialogs with the EQ band editor: themed title/value header, explicit MIN/MAX range, large input surface, live value preview slider, and matching Back / Previous / Next / Save actions. Previous/Next commits the current valid field and opens the adjacent registered control; Back/Close discards the uncommitted preview.
- Fix the numeric-dialog Kotlin compile error caused by an undeclared `previewValue`: typed values now use the shared `updatePreview(...)` routine, keeping the input, value header, slider, and preview note synchronized without recursive TextWatcher updates. Save and Previous/Next still commit valid values; Back/Close still discard pending edits.
- Keep this follow-up correction within V3.0.15: `versionName = "3.0.15"` and `versionCode = 92` remain unchanged.
- Make numeric input and action surfaces Custom-theme aware. Custom surface/edge colors are used only in CUSTOM mode; built-in Light, Dark, System, and Glossy use their own input, divider, chip, and text contrast palettes.
- Strengthen Bar Appearance preset navigation and Open All Looks contrast by explicitly applying the active Theme Studio foreground/background pair. Center and bold the Bar Appearance preset subheading; increase spacing and refine the section divider treatment for BAR SCALE, BAR APPEARANCE, and RESPONSE ENGINE.
- Add a dedicated Save To destination picker in Floating Scene Manager. It lists existing saved scenes and replaces the selected scene's configuration with the current live Floating Profile/layer/appearance state while preserving the target scene ID, name, and creation metadata. It does not create duplicate scenes.
- Keep versionName at 3.0.15; increment only versionCode from 91 to 92 for this corrective build. Audio engine/controller, EQ preset schema, and visualizer FFT/rendering paths remain unchanged.

## V3.0.15 — Theme Palette Isolation & Response Engine Controls (versionCode 91)
- Tighten Theme Studio palette isolation across MainActivity and EqualizerActivity: GLASS uses its built-in purple accent, LIGHT uses a light neutral palette with dark controls, DARK uses a dark surface with high-contrast light controls, SYSTEM follows Android day/night mode, and only CUSTOM consumes the saved custom accent/surface/edge values.
- Restrict migration of the legacy shared text-color preference to CUSTOM only. This prevents a value previously saved while Custom was selected from being copied into the built-in GLASS palette.
- Split the visual settings into BAR SCALE, BAR APPEARANCE, and RESPONSE ENGINE sections with clearer descriptions and input chips consistent with the existing themed numeric editor.
- Make FFT Size and Hop Size sliders discrete and explicit: FFT 1024 / 2048 / 4096 points; Hop 256 / 512 / 1024 / 2048 PCM samples, dynamically capped by the active FFT size. The matching numeric editor normalizes to the supported choice.
- Response Speed remains a 0–100% animation-smoothing control: lower values produce softer/slower bar movement; higher values follow target levels more quickly. It does not increase the platform audio callback rate.
- FFT Size controls the analysis/capture window. At 44.1 kHz, 1024 / 2048 / 4096 correspond approximately to 23.2 / 46.4 / 92.9 ms windows and 43.1 / 21.5 / 10.8 Hz per-bin spacing. The standard Android Visualizer uses its capture-size API and device-supported range; MediaProjection PCM uses the selected number of samples.
- Hop Size controls the distance between successive PCM analysis windows in the MediaProjection path. At 44.1 kHz, 256 / 512 / 1024 / 2048 correspond approximately to 5.8 / 11.6 / 23.2 / 46.4 ms. The standard Android Visualizer API does not expose a Hop Size setting; its callback cadence is system/vendor controlled.
- Keep the EQ audio controller, EQ service, preset schema, and floating renderer architecture unchanged. Persist FFT and Hop settings and apply them to the relevant capture path when updated.
- Keep the existing Preamp safety lockout on the experimental output-mix route. LoudnessEnhancer/iJoysoft volume-booster investigation remains research for a future player-owned session, not part of this release.

## V3.0.14 — Compile, FFT Decode & Real-Time Responsiveness Fix (versionCode 90)
- Fix the duplicate `EqualizerActivity.onResume()` override by keeping one lifecycle handler that refreshes Theme Studio settings and reapplies the EQ palette. This resolves the Kotlin conflicting-overloads compile error without changing the public app version.
- Correct Android `Visualizer` packed FFT decoding: DC (`fft[0]`) and Nyquist (`fft[1]`) are special single-real bins; bins 1..N/2-1 use interleaved real/imaginary pairs. This prevents the Nyquist value from being incorrectly mixed into the DC/lowest-frequency bin and improves low-frequency band analysis.
- Select positive FFT bins for the user-selected bass range while preserving 0–0 as full-range mode; DC is excluded from the bass peak calculation because it is not an audible bass tone.
- Remove stacked per-capture smoothing from the FFT analysis path. Raw band levels are now passed to the display-synchronised animation stage, so the existing Response Speed setting is the single smoothing control instead of being layered on top of another fixed filter.
- Reduce the optional MediaProjection PCM analyzer window from 4096 to 2048 samples and hop from 1024 to 512 samples. At 44.1 kHz this changes the analysis window from about 92.9 ms to 46.4 ms and the update hop from about 23.2 ms to 11.6 ms, trading some low-frequency bin resolution for better transient timing.
- Coalesce spectrum frames before main-thread rendering and level broadcasts. When the UI thread is busy, the service consumes the newest FFT frame instead of queuing stale frames that can make bars appear delayed.
- Add diagnostic Logcat measurements for Visualizer capture size, FFT bin resolution, analysis window, requested/delivered callback rate, analysis cost, and MediaProjection window/hop. This helps distinguish audio capture limits from rendering delays on each device.
- Keep `versionName = "3.0.14"`; increment only Android `versionCode` from 89 to 90 for this corrective build. EQ audio controller, EQ service, preset schema, Theme Studio, and floating renderer appearance remain unchanged.

## V3.0.14 — Theme Palette Isolation, Effect Input Dialogs & Notification State Restore
- QA refinement: landscape Display Live sizing now scales from the long display axis; custom-theme text applies a WCAG-style contrast fallback; HEX editing synchronizes live with HSV and wheel; a notification-started Visualizer also restores EQ service when the saved EQ switch was already ON.
- Match Bass Boost and Virtualizer numeric value dialogs to the EQ band editor: large themed input, min/max range, live preview slider, keyboard entry, and rollback on Back/Close; only Save commits the profile state.
- Isolate EqualizerActivity palettes by Theme Studio mode. GLASS, LIGHT, DARK, and SYSTEM use their own built-in surface/edge colors; only CUSTOM reads stored custom surface and glossy-edge colors. SYSTEM follows the current device night mode when the EQ Activity resumes.
- Extend the Theme Studio panel-background and glossy-edge pickers with synchronized Hue, Saturation, and Brightness controls. Color wheel, HSV sliders, preview, and HEX input now represent the same selected color.
- Enlarge Display Live's horizontal preview canvas in landscape and recalculate preview-bar height from the available viewport without changing the Floating renderer geometry.
- Add press-and-hold repeat movement to the floating-layer position dialog's four directional arrows; callbacks are cancelled when the dialog closes.
- Fix notification cold-start recovery: a recreated FloatingVisualizerService hydrates saved geometry, mode, colors, effect, overlay state, and floating-profile library from SharedPreferences before handling action-only notification intents. Saved profile colors are converted to the runtime format used by the layered renderer.
- Keep the existing output-mix EQ engine, Preamp safety lockout, Bass Boost, Virtualizer, Limiter, and Reverb capability policy unchanged.
- Research note for a future update: iJoysoft's public listing advertises a volume booster up to 200%, but does not disclose its internal DSP path. Android LoudnessEnhancer is a possible session-based loudness backend for a future player-owned audio session; it is not implemented in this build and does not imply global processing.
- App version 3.0.14 / versionCode 89.

## V3.0.13 — Visible Play/Pause Switch & Preset Cascade
- Replaced the switch thumb with an intrinsic-sized theme-aware Play/Pause drawable so the glyph remains visible and follows the native thumb animation.
- Applied the same Play/Pause switch style to MainActivity overlay/capture controls and EqualizerActivity controls.
- Reworked the preset PopupWindow entrance with a clear downward reveal and staggered row animation (approximately 1.0–1.5 seconds depending on preset count).
- Preserved audio engine, EQ controller, preset repository, and visualizer renderer behavior.

## V3.0.12 — EQ Effect Layout, Motion & Theme Switch Refinement
- Replace the generic circular status marker with media-style Play/Stop glyphs in the EQ status pill; use a theme-aware Play/Pause glyph inside EQ switch thumbs to make ON/OFF states clearer.
- Reorder the EQ effects into an enabled-controls group (Bass Boost, Virtualizer, Limiter) followed by a separated compatibility-restricted group (Preamp, Reverb), with an explicit disabled-feature explanation.
- Keep Preamp disabled on the experimental output-mix route; retain saved values without applying them.
- Add a short scale-and-slide entrance animation to the Equalizer screen and a matching downward scale/slide reveal for the preset dropdown, while preserving the active Theme Studio palette.
- Keep EQ controller, audio session routing, preset schema, fader interaction, and Visualizer rendering logic unchanged.
- App version 3.0.12 / versionCode 86.

## V3.0.11 — Display Live Compile Fix
- Fix the unresolved Kotlin `roundToInt()` extension in `MainActivity.kt` by explicitly importing `kotlin.math.roundToInt`. This resolves the compile errors in responsive Display Live bar sizing and margin calculations without changing the feature behavior.
- Keep the public app version at 3.0.11. Increment only the internal Android `versionCode` to 85 so CodeAssist/Android can distinguish this corrective APK from the previous V3.0.11 build.

## V3.0.11 — Effect Controls, Profile Drag Auto-Scroll & Display Fit
- Rework EQ effect controls into a consistent value-chip + notification-style pill switch row. Switch track/thumb now use the active Theme Studio accent and light/dark disabled colors.
- Improve disabled feedback and spacing for Preamp, Bass Boost, Virtualizer, Limiter, and Reverb sections; clickable numeric controls remain aligned beside their switches.
- Add continuous edge auto-scroll while holding and dragging a Floating Profile row near the top/bottom of the profile canvas. Drop-index calculation now includes the canvas scroll offset.
- Add a viewport-fixed, Theme Studio-aware vertical liquid-glass scroll bubble to the Floating Profile canvas.
- Make Display Live horizontal bars fill the measured preview width using responsive equal cells, adaptive bar width, and balanced gaps in portrait and landscape layouts.
- Set the default glow for newly created profiles/effect defaults to 0%; explicit user settings and appearance presets remain unchanged.
- Keep EQ audio backend/controller, renderer service, and existing preset data format unchanged.
- App version 3.0.11 / versionCode 84.

## V3.0.10 — Preamp Output-Safety & Control Refinement
- Fail closed on Preamp for the current experimental output-mix session 0. The control is visibly disabled because the target device showed unpredictable output/mute behavior; stored Preamp values are retained as dormant profile data and are not written to the output effect.
- Read back DynamicsProcessing InputGain before accepting a newly created output-mix engine. No InputGain writes are issued on session 0; if a non-unity/stale value is detected, reject that DSP instance and fall back to the native EQ backend.
- Migrate legacy profiles with Preamp enabled to Preamp OFF without deleting their saved dB value. This prevents automatic service restoration from reapplying the problematic output-mix gain.
- Keep Bass Boost, Virtualizer, Limiter, 5/10-band EQ, and preset behavior unchanged; add explicit safety explanation to Preamp capability UI.
- Standardize disabled/checked switch tint states and enlarge pill-switch touch height across MainActivity and EqualizerActivity.
- Retain the isolated Floating Profile canvas scroll view and dedicated grip-to-canvas scrolling; verify gestures inside the profile canvas remain nested while gestures outside continue to scroll the main page.
- App version remains 3.0.10 / versionCode 83 for this work branch.

## V3.0.9 — Live Fader-to-Engine Output Fix (versionCode 82)

- Fix the missing audio-engine write in the custom vertical fader callback. Previously, dragging a fader updated only the UI/working profile; the EQ engine was not receiving `setBand(...)`, so the actual effect remained at its previous value and `onPause()` read-back restored the old gain.
- Route every accepted vertical fader change through `EqEngineController.setBand(...)` and read back the value from the active DynamicsProcessing/Native Equalizer backend. The fader thumb, dB label, and Current State now follow the engine-confirmed gain.
- Apply the same gain to every configured DynamicsProcessing channel to keep left/right output consistent; verify control ownership again after the write and report control loss rather than silently persisting an un-applied value.
- Persist the engine-confirmed working profile at the end of a fader gesture or cancelled gesture, instead of writing SharedPreferences on every MotionEvent. Activity pause/destroy continues to capture the active effect read-back as a final safeguard.
- Keep `versionName = "3.0.9"`; increment `versionCode` from 81 to 82 so Android/CodeAssist installs this corrective build as a newer package. No visualizer renderer or audio-session routing strategy was changed.

## V3.0.9 — Android Package Version Metadata Fix
- Keep versionName at 3.0.9 and increment versionCode to 81 so Android/CodeAssist recognizes this package as a new installable build and invalidates the prior 3.0.8 package metadata.

## V3.0.9 — Precision Mixer Faders & Preset Library

- Replace rotated native SeekBars with correctly measured custom vertical mixer faders. Drawing bounds, thumb geometry and touch bounds now match; the long fader travel reduces overlap with value labels, frequency labels and reset controls.
- Expand the band canvas height and fader travel. Five-band engines remain centered without horizontal scrolling; 10-band engines show five faders at a time with responsive horizontal paging.
- Route horizontal drags from the fader surface into the band viewport while vertical drags adjust gain. This avoids the outer page ScrollView stealing fader gestures and avoids scroll jitter from competing touch interception.
- Fix band value entry by allowing the numeric keyboard to open. Typing and the dialog slider preview values live in the engine and synchronize with the main fader; Save/Previous/Next commit, while Back/X restore the original value.
- Add default 5-band and 10-band starter presets (Flat, Bass, Vocal and tonal profiles) without overwriting user presets. Map presets both from 5 to 10 bands and 10 to 5 bands using logarithmic-frequency interpolation when exact centers differ; retain the source preset data.
- Keep Theme Studio, audio controller, Visualizer renderer and floating service implementation unchanged.

## V3.0.8.1 — Notification Stop Icon Compile Fix

- Replaced the unavailable `android.R.drawable.ic_media_stop` reference with the app-owned vector drawable `R.drawable.ic_notification_stop`.
- Kept the existing Start Visualizer play icon and notification action behavior unchanged.
- No EQ engine, audio routing, preset, or visualizer rendering logic changed.

## V3.0.7 — EQ Navigation & Precision Input Polish
- Replace the native Spinner preset control with a Theme Studio-aware glass dropdown popup, including selected-state markers, supporting text and scrollable preset library.
- Reuse the main Visualizer's horizontal bubble scroll-indicator design for the EQ band viewport; it tracks the real viewport position and is hidden when the detected band list fits without scrolling.
- Keep five-band layouts centered without horizontal scrolling; larger band sets retain the five-fader viewport and a live indicator.
- Add tap-to-edit numeric dB dialogs for individual bands with validated engine ranges, Back/Next/Done actions, read-back updates, persistence and automatic viewport focus when moving to the next band.
- Refine the top title to “Equalizer Modern Engine” with a restrained accent-glass gradient, and improve information spacing below the band canvas.
- Preserve Theme Studio theme, language and text-scale integration. Audio engine/controller, preset serialization and FloatingVisualizerService are unchanged.

## V3.0.6 — EQ Mixer UI & Preset Workflow Polish
- Center the inactive EQ scan guidance in the full band canvas; five-band backends use a centered, non-scroll layout, while more than five real bands use a horizontally scrollable five-fader viewport with subtle glass edge rails.
- Add an individual 0 dB reset control to each detected band and preserve read-back values in the current working state.
- Increase spacing, heading hierarchy, section separators, and touch targets for a cleaner Theme Studio-integrated mixer layout.
- Add a live Current State / Preset status above the preset selector; replace the generic Save action with Save To, which writes the current configuration into an existing named preset and guides users to create one when none exist.
- Restyle the preset add control and EQ master state indicator while preserving Theme Studio colors, light/dark contrast, language and text scaling.
- Preserve audio engine, Visualizer renderer and service implementation from V3.0.5.

## V3.0.5 — Theme Studio Integrated EQ UI
- Link EqualizerActivity visual palette to MainActivity Theme Studio preferences: Glass, Custom, System, Dark and Light modes, accent/surface/edge/text colors, Indonesian/English language and UI text scale.
- Restyle EQ panel, band canvas, preset selector, action buttons, switches and slider tracks/thumbs using rounded liquid-glass surfaces and adaptive light/dark contrast.
- Add a live traffic-light EQ status line to the MainActivity EQ entry card; refresh it when returning from EqualizerActivity and style it with the active Theme Studio palette.
- Keep the audio engine/controller, preset data format, and FloatingVisualizerService renderer unchanged; this release is a UI integration update.

## V3.0.3 — Preamp & Clear EQ Capability State
- Add a switchable DynamicsProcessing input-gain Preamp before the EQ stage, with a -12 dB to +6 dB slider and live read-back. It is enabled only when the active API 28+ DynamicsProcessing backend has control; native Equalizer fallback reports it unsupported.
- Persist Preamp enabled state and gain in working state, named presets, portable `.dj-eq.json`, profile switching, and foreground-service restoration. Existing presets migrate with Preamp off by default.
- Keep Reverb visible as a disabled capability control. Auxiliary Reverb is reported as detected but requires a playback owner to attach the effect ID and set send level; vendor post-processing requires privileged/system integration. No global enable claim is made.
- Show an actionable message inside the EQ band canvas when the EQ engine has not been activated, instead of leaving an apparently empty control area.
- Preserve limiter behavior as a dynamic stage; the user controls enable/disable while threshold/ratio/attack/release remain fixed defaults in this release.
- Preserve MainActivity and FloatingVisualizerService renderer sources.

## V3.0.2 — Limiter & Preset State Engine
- Add a real DynamicsProcessing limiter stage to the API 28+ 10-band configuration; leave it bypassed by default and allow explicit enable/disable when the active backend supports it.
- Persist limiter enable state in the working profile and portable EQ preset JSON.
- Distinguish the unsaved working configuration as “Current State” in the preset selector, while retaining named presets independently.
- Replace the ambiguous unsupported-band note with an explanation that unavailable preset frequency points are retained but not processed by the active backend.
- Add runtime Reverb descriptor inventory and device/API diagnostics. Keep Reverb disabled for external-app/global playback because auxiliary Reverb requires each player to attach its output and set a send level; vendor post-processing descriptors are reported but not treated as app-accessible controls.
- Keep MainActivity and FloatingVisualizerService renderer sources unchanged from V3.0.1/V2.4.32 baseline.

## V3.0.1 — Adaptive EQ Engine
- Add a 10-band DynamicsProcessing pre-EQ backend probe for API 28+; use it only when the runtime exposes the effect and grants control on the experimental output session.
- Keep the native Equalizer backend as an honest fallback; display the actual band count reported by the active backend and never create simulated sliders.
- Preserve and restore the current unsaved EQ state independently from named presets, including band gains, Bass Boost, and Virtualizer values.
- Reapply the saved working state when the foreground EQ service recreates native effect handles.
- Preserve old five-band profiles when opening a 10-band backend; use explicit logarithmic interpolation when expanding an older smaller profile and retain a mapping warning.
- Keep the Visualizer renderers and V2.4.32 baseline code unchanged.

## V3.0.0 build fix
- Corrected `ScrollView.fillViewport` to Kotlin synthetic property `isFillViewport` in `EqualizerActivity`.

## V3.0.0
- Preserve the v2.4.32 Visualizer renderer and floating overlay implementation as the baseline.
- Add a separate EqualizerActivity and an isolated AudioEffect controller with runtime capability probing.
- Add output-mix (session 0) experimental EQ/BassBoost/Virtualizer support with explicit legacy/experimental status; no global-audio guarantee is made.
- Add runtime-native band display, read-back values, preset save/rename/delete/reset, and JSON import/export.
- Prevent the outer MainActivity ScrollView from intercepting gestures that begin inside Floating Profile's nested scroll canvas.
- Reverb is shown as a future/unsupported route-dependent feature; no fake active control is exposed.

## v2.4.32 baseline
- Restore the complete v2.4.30 Display/Choreographer preview scheduling and live interpolation; remove the v2.4.31 preview pausing, 30 FPS pacing, and parent-only invalidation changes that could leave the Display preview visually frozen.
- Reduce only the in-app Display preview glow; keep the full Floating Service renderer unchanged.
- Keep Display and Floating renderers running simultaneously.


## V3.0.8 — Mixer Controls & Notification Recovery
- Enlarged vertical EQ fader hit area and mixer-style rectangular fader cap; fader touch now disallows parent scroll interception while dragging.
- Band gain editor adds a horizontal precision slider with live preview, min/max validation, Previous/Next navigation, Save commit, and Back/X rollback.
- Preamp, Bass Boost, Virtualizer, Limiter, and Reverb controls are separated into themed effect cards; boost controls show percentage readouts and dependent controls visibly disable while EQ is off.
- Added Equalizer Engine toggle to the Visualizer foreground notification and renamed the audio row to Start/Stop Visualizer. Notification state reads the persisted EQ and floating settings; EQ service refreshes the host notification after lifecycle changes.
- Export now asks whether to export Current State or a named preset. Import asks whether to apply to Current State or save as a new preset.


## V3.0.18.1 — Stereo Playback Capture & Spectrum Consistency

- **Stereo MediaProjection capture:** Prefer stereo PCM capture and run independent left/right FFTs before combining magnitudes. This avoids cancellation of anti-phase vocal, chorus, and stereo material caused by a pre-FFT mono downmix. Fall back to mono only when the device cannot initialize stereo capture, and log/report the channel mode.
- **Full-bin logarithmic analysis:** Measure every FFT bin within each logarithmic horizontal band using RMS energy plus peak retention, instead of probing only five arbitrary frequencies. This reduces missed mid/high harmonics between sample points.
- **Fractional Single-frequency ranges:** Measure narrow Single/DJ frequency ranges through interpolation when the selected range is narrower than the device's FFT resolution, rather than snapping to an unrelated whole bin.
- **Visualizer device capability:** Use the maximum callback rate reported by `Visualizer.getMaxCaptureRate()` without an additional fixed 20 Hz cap. Log requested versus actually applied capture size, sampling resolution, and callback rate. Explicitly request normalized Visualizer scaling when supported.
- **Source-specific controls clarified:** Response remains time-based bar animation smoothing. Hop Size is used only by the MediaProjection PCM path; the standard Android Visualizer path uses its device-reported callback cadence.
- **Version:** `versionName 3.0.18.1`, `versionCode 96`.
