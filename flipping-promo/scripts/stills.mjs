// Render a set of preview stills: node scripts/stills.mjs outDir frame1 frame2 ...
import { bundle } from "@remotion/bundler";
import { renderStill, selectComposition } from "@remotion/renderer";
import path from "node:path";

const [outDir, ...frames] = process.argv.slice(2);
const serveUrl = await bundle({ entryPoint: path.resolve("src/index.ts") });
const composition = await selectComposition({ serveUrl, id: process.env.COMP ?? "FlippingPromo" });
for (const frame of frames.map(Number)) {
  await renderStill({
    serveUrl,
    composition,
    frame,
    output: path.join(outDir, `f${String(frame).padStart(3, "0")}.jpg`),
    imageFormat: "jpeg",
    jpegQuality: 85,
    scale: Number(process.env.SCALE ?? 0.5),
  });
  console.log("frame", frame);
}
