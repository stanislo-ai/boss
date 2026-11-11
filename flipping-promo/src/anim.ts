import { interpolate, spring } from "remotion";
import { EASE_IN, EASE_OUT, clamp } from "./theme";
import { FPS, OVERLAP } from "./timing";

/** Spring that starts at local frame `at`. Bouncy-but-classy default. */
export const sp = (
  f: number,
  at: number,
  config: { damping?: number; stiffness?: number; mass?: number } = {},
) =>
  spring({
    frame: f - at,
    fps: FPS,
    config: { damping: 15, stiffness: 140, mass: 0.9, ...config },
  });

/** 0 -> 1 eased progress between two local frames. */
export const prog = (f: number, a: number, b: number, easing = EASE_OUT) =>
  interpolate(f, [a, b], [0, 1], { ...clamp, easing });

/**
 * Exit progress for a scene of local length `len` (which includes OVERLAP).
 * Starts a few frames before the downbeat and finishes OVERLAP frames after.
 */
export const exitP = (f: number, len: number, lead = 4) =>
  interpolate(f, [len - OVERLAP - lead, len], [0, 1], {
    ...clamp,
    easing: EASE_IN,
  });

export const mix = (a: number, b: number, t: number) => a + (b - a) * t;

/** Decaying camera shake after a hit at local frame `at` (returns px). */
export const shake = (f: number, at: number, amp = 14) => {
  const d = f - at;
  if (d < 0) return { x: 0, y: 0 };
  const e = Math.exp(-d / 3.5) * amp;
  return { x: Math.sin(d * 2.7) * e, y: Math.cos(d * 3.3) * e * 0.7 };
};
