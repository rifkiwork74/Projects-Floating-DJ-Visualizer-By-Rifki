# Floating DJ Visualizer 2.2.3 — Android 16 Audio Response Update

## User-facing changes

- Replaced the two-target hue ring + inner globe picker with one circular HSV disk and one draggable knob. Knob angle selects Hue and knob radius selects Saturation; the Brightness slider remains independent.
- Color-wheel drag now disallows parent ScrollView interception while the pointer is down, then releases the parent on ACTION_UP/CANCEL. The profile editor keeps the full vertical scroll path and a taller bounded dialog.
- Bar Appearance numeric fields use fixed-width, centered value boxes and consistent row heights. Slider knobs are larger (24dp), accent-tinted and easier to track.
- Added **Response Speed (0–100%)**. 0 is smoother/softer; 100 is faster attack and release. The setting is synchronized with both primary Visualizer and MediaProjection FFT paths, floating bars, live preview and saved color profiles.
- Did not add fake 1024/2048/4096 FFT-size choices: Android's Visualizer capture range is device-reported and may not support all those values. Response Speed instead controls the actual view/audio smoothing path in both engines.
- NEON now separates an aura from the opaque bar body. The body remains filled at the chosen opacity, while four low-cost translucent rounded layers create brighter edge light. No blur mask filter is applied per frame.
- Start/Stop is one full-width stateful control: START, STOP, or RESUME depending on engine state.
- Notification uses the native Android MediaStyle template with three controls in this order: FLOATING ON/OFF, START/PAUSE/RESUME, M.PROJ ON/OFF. The third action opens the app for the system-required MediaProjection consent or to disable backup. The manifest declares POST_NOTIFICATIONS; the app does not interrupt audio start with a separate notification-permission prompt because MediaStyle media-session notifications have a documented exemption on Android 13+.
- Color profiles now persist Response Speed too; older profiles load with the default value (82%).

## Notification implementation note

Android 12+ decorates and constrains fully custom notification layouts, so this release uses the native MediaStyle control row rather than a bespoke RemoteViews layout with fake switches. The three controls are stateful action buttons; the state is reflected in their labels and icons.
