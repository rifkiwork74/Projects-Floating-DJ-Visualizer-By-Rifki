# Floating DJ Visualizer 2.2.2 — Android 16 Audio/UI Audit

## Changes in this package

- Light appearance mode now gives numeric controls and editor fields a light input surface with readable dark text. Dark themes keep their dark glass surface and light text.
- Added a draggable HSV color wheel to both the five-stop spectrum profile editor and the interface accent editor. Both support HEX; spectrum and accent editors also expose numeric RGB values. Existing color-profile save/load/rename/delete functions are preserved.
- NEON uses three translucent aura layers rather than depending only on `Paint.setShadowLayer`. The drawing remains bounded to each bar and avoids a blur filter on every frame.
- Single-bar Live Spectrum captions become `SINGLE / DJ BASS / LIVE LEVEL` or `SINGLE / MUSIC FULL / LIVE LEVEL`; horizontal mode keeps `BASS / MID / TREBLE`.
- Position Studio, Spectrum Profile Editor, and Appearance Accent Studio open centered and can be moved by dragging their header. Dialog movement is clamped to the display. Position Studio retains its D-pad, step input, and 0°–359° rotation controls.
- Audio start is independent from overlay permission. START requests RECORD_AUDIO only. The Floating Bar Overlay switch requests overlay permission only when switched on. Turning it off removes the overlay but leaves the audio engine running.
- Notifications provide Show/Hide Floating, Pause/Resume Audio, and Backup actions. Backup opens the app and toggles the optional MediaProjection control; Android's own consent screen is still shown for each new projection session.
- Overlay enabled state and notification-paused service state are persisted. Existing `floating_dj_settings` preferences are retained.
- Target SDK and compile SDK remain 36. Version is `2.2.2-android16-audio-ui-audit`, versionCode 6.

## User flow

1. Open the app: no overlay-permission prompt is shown.
2. Press START: grant RECORD_AUDIO. The in-app Live Spectrum can run without enabling the floating overlay.
3. Turn on FLOATING BAR OVERLAY: grant Display over other apps when requested. The floating bar appears when an audio engine is active.
4. Turn on BACKUP AUDIO CAPTURE only if the primary Visualizer output-mix path is unavailable. Android presents its MediaProjection consent UI.
5. Notification actions let the user show/hide the overlay, pause/resume audio analysis, or open the app to toggle backup.

## Verification

- All 20 XML files parsed successfully.
- All 35 referenced `R.id` values were found in layout/resources.
- All `FloatingVisualizerService` action/extra references used by `MainActivity` have matching service constants.
- No duplicate XML IDs or incorrect `MainActivity` `dp(number)` calls were found.
- Kotlin parser reported no syntax diagnostics in the source files.
- ZIP integrity checks passed for both deliverables.

**Build limitation:** this environment has no Android SDK/`android.jar` or Gradle executable, so a full Android/CodeAssist compile and device runtime test could not be run here. Static validation is not a successful APK build. Import this package in CodeAssist and run Build before installing.
