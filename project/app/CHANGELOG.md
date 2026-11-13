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

## V3.0.2
- Add an optional limiter stage to the existing DynamicsProcessing 10-band backend, disabled by default and controlled only when the active effect grants control.
- Save and restore Limiter state with working EQ state and named/imported presets.
- Show “Current State” when live values differ from the selected saved preset; preserve named preset entries.
- Clarify unsupported frequency points and display Reverb capability descriptors with device/API details. Reverb remains unavailable as a global external-app control without an attach/send path or OEM route integration.

## v3.0.1
- Add adaptive 10-band DynamicsProcessing backend probing with native Equalizer fallback.
- Persist live EQ/Bass Boost/Virtualizer state separately from named presets and restore it after Activity/service recreation.
- Add explicit log-frequency interpolation when expanding older smaller EQ profiles; preserve unsupported points.

## v3.0.0
- Add modular EQ Activity, audio controller, preset repository, capability probe, and foreground service.
- Keep the existing Floating DJ Visualizer renderer intact.


## V3.0.8 — Mixer Controls & Notification Recovery
- Enlarged vertical EQ fader hit area and mixer-style rectangular fader cap; fader touch now disallows parent scroll interception while dragging.
- Band gain editor adds a horizontal precision slider with live preview, min/max validation, Previous/Next navigation, Save commit, and Back/X rollback.
- Preamp, Bass Boost, Virtualizer, Limiter, and Reverb controls are separated into themed effect cards; boost controls show percentage readouts and dependent controls visibly disable while EQ is off.
- Added Equalizer Engine toggle to the Visualizer foreground notification and renamed the audio row to Start/Stop Visualizer. Notification state reads the persisted EQ and floating settings; EQ service refreshes the host notification after lifecycle changes.
- Export now asks whether to export Current State or a named preset. Import asks whether to apply to Current State or save as a new preset.
