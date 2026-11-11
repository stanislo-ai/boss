import { Easing } from "remotion";

export const FONT_DISPLAY = "SF Pro Display";
export const FONT_TEXT = "SF Pro Text";

export const C = {
  bg: "#030305",
  white: "#f5f5f7",
  grey: "#a1a1a6",
  dim: "#6e6e73",
  teal: "#2fd6c8",
  blue: "#2c7bff",
  violet: "#8a5cff",
  silver: "#d9dce3",
};

// Apple-like curves
export const EASE_OUT = Easing.bezier(0.16, 1, 0.3, 1); // expo-ish out
export const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
export const EASE_IN = Easing.bezier(0.7, 0, 0.84, 0);

export const clamp = {
  extrapolateLeft: "clamp",
  extrapolateRight: "clamp",
} as const;
