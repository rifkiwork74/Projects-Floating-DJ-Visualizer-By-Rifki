# Floating DJ Visualizer 2.3.3 — Overlay Stability Update

- Disabled dialog dragging completely; every studio dialog stays centered and can only be closed with its in-panel close control or action buttons.
- Removed live dialog-window position mutation from touch handling to prevent accidental header gestures from destabilizing the app.
- Appearance Studio dimensions and controls now accept zero values and broader bounded manual ranges (up to 4096 dp for width/height); the renderer safely fits oversized requests to the active display.
- A bar count of zero is now handled safely by both FFT paths and displays no horizontal bars without division-by-zero.
- Floating bars now reserve transparent drawing margins for their glow, and the glow intensity renders in the overlay for all appearance styles rather than only NEON.
- Primary Visualizer(0), FFT smoothing, and MediaProjection capture pipeline are preserved.

