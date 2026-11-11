// Global timing, locked to the beat grid of "Cold" (≈143.5 BPM).
export const FPS = 30;
export const WIDTH = 1080;
export const HEIGHT = 1920;
export const DURATION_S = 14;
export const DURATION = DURATION_S * FPS;

/** seconds -> frames */
export const s = (sec: number) => Math.round(sec * FPS);

// Beat times detected from the track (seconds).
export const BEATS = [
  0.67, 1.07, 1.51, 1.93, 2.35, 2.79, 3.2, 3.62, 4.06, 4.48, 4.9, 5.32, 5.74,
  6.18, 6.59, 7.01, 7.43, 7.87, 8.29, 8.68, 9.13, 9.54, 9.98, 10.4, 10.82,
  11.22, 11.66, 12.07, 12.49, 12.91, 13.33, 13.77,
];
export const beat = (i: number) => s(BEATS[i]);

// Scene boundaries (each starts on a bar / downbeat).
export const SCENES = {
  hook: { from: 0, to: beat(4) }, // 0.00 – 2.35
  perfumes: { from: beat(4), to: beat(8) }, // 2.35 – 4.06
  airpods: { from: beat(8), to: beat(12) }, // 4.06 – 5.74
  montage: { from: beat(12), to: beat(16) }, // 5.74 – 7.43
  bento: { from: beat(16), to: beat(24) }, // 7.43 – 10.82
  outro: { from: beat(24), to: DURATION }, // 10.82 – 14.00
} as const;

// Overlap so one scene can animate out while the next animates in.
export const OVERLAP = 6;
