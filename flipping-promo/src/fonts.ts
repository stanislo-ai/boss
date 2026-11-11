import { loadFont } from "@remotion/fonts";
import { staticFile } from "remotion";
import { FONT_DISPLAY, FONT_TEXT } from "./theme";

const faces: [string, string, string][] = [
  [FONT_DISPLAY, "SF-Pro-Display-Regular.otf", "400"],
  [FONT_DISPLAY, "SF-Pro-Display-Medium.otf", "500"],
  [FONT_DISPLAY, "SF-Pro-Display-Semibold.otf", "600"],
  [FONT_DISPLAY, "SF-Pro-Display-Bold.otf", "700"],
  [FONT_TEXT, "SF-Pro-Text-Regular.otf", "400"],
  [FONT_TEXT, "SF-Pro-Text-Medium.otf", "500"],
  [FONT_TEXT, "SF-Pro-Text-Semibold.otf", "600"],
  [FONT_TEXT, "SF-Pro-Text-Bold.otf", "700"],
];

export const fontsLoaded = Promise.all(
  faces.map(([family, file, weight]) =>
    loadFont({
      family,
      url: staticFile(`fonts/${file}`),
      weight,
      format: "opentype",
    }),
  ),
);
