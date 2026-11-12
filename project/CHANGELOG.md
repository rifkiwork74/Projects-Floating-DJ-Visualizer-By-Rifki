## v2.4.32
- Restore the complete v2.4.30 Display/Choreographer preview scheduling and live interpolation; remove the v2.4.31 preview pausing, 30 FPS pacing, and parent-only invalidation changes that could leave the Display preview visually frozen.
- Reduce only the in-app Display preview glow: replace its three-pass halo with one smaller, lower-alpha pass and remove redundant glow passes from live/held peak markers.
- Keep the full Floating Service glow renderer, FFT delivery, 20 Hz–20 kHz mapping, frame-synchronised animation, Single/Horizontal geometry, and overlay position/rotation logic from v2.4.30 unchanged.
- Keep Display and Floating renderers running simultaneously; no scroll-triggered pause or lower Display frame cap is introduced.
- Bumped version to 2.4.32 / versionCode 61.

