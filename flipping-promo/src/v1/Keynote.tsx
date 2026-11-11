import React from "react";
import { AbsoluteFill, Sequence } from "remotion";
import { Finish } from "../components/Background";
import { LiquidGlassFilter } from "../components/Glass";
import { Hit, Mix } from "../shared/Mix";
import { BEATS, DURATION, OVERLAP, SCENES, s } from "../timing";
import {
  K1Hook,
  K2Perfumes,
  K3AirPods,
  K4Montage,
  K5Proof,
  K6Outro,
} from "./KeynoteScenes";
import "../fonts";

const b = (i: number) => s(BEATS[i]);
const len = (k: keyof typeof SCENES, extra = OVERLAP) =>
  Math.min(SCENES[k].to + extra, DURATION) - SCENES[k].from;

const HITS: Hit[] = [
  { at: 0, sfx: "swish", vol: 0.3 },
  { at: b(0), sfx: "impact", vol: 0.55 },
  { at: b(2), sfx: "tick", vol: 0.4 },
  { at: b(2) + 6, sfx: "tick", vol: 0.35 },
  { at: b(3), sfx: "tick", vol: 0.4 },
  // zoom-through into the letter
  { at: SCENES.perfumes.from - 16, sfx: "riser", vol: 0.0 },
  { at: SCENES.perfumes.from - 14, sfx: "whoosh", vol: 0.55 },
  { at: SCENES.perfumes.from, sfx: "thump", vol: 0.45 },
  { at: SCENES.perfumes.from + 8, sfx: "pop", vol: 0.3 },
  // light flip
  { at: SCENES.airpods.from - 8, sfx: "swish", vol: 0.4 },
  { at: SCENES.airpods.from, sfx: "impact", vol: 0.4 },
  { at: SCENES.airpods.from + 6, sfx: "shimmer", vol: 0.2 },
  // strips
  ...[12, 13, 14, 15].map((i) => ({ at: b(i), sfx: "thump", vol: 0.38 })),
  ...[12, 13, 14, 15].map((i) => ({ at: b(i) - 2, sfx: "swish", vol: 0.22 })),
  // proof
  { at: SCENES.bento.from - 8, sfx: "whoosh", vol: 0.4 },
  { at: SCENES.bento.from, sfx: "impact", vol: 0.4 },
  { at: SCENES.bento.from + 2, sfx: "roll", vol: 0.45 },
  { at: b(20), sfx: "roll", vol: 0.45 },
  { at: b(20), sfx: "tick", vol: 0.3 },
  { at: b(22), sfx: "pop", vol: 0.4 },
  // outro
  { at: SCENES.outro.from - 36, sfx: "riser", vol: 0.32 },
  { at: SCENES.outro.from - 27, sfx: "swell", vol: 0.3 },
  { at: SCENES.outro.from, sfx: "impact", vol: 0.6 },
  { at: SCENES.outro.from + 8, sfx: "shimmer", vol: 0.45 },
  { at: b(26), sfx: "pop", vol: 0.4 },
  { at: b(28), sfx: "tap", vol: 0.55 },
];

export type VProps = { music: boolean };

export const Keynote: React.FC<VProps> = ({ music }) => (
  <AbsoluteFill style={{ backgroundColor: "#030305" }}>
    <LiquidGlassFilter />
    <Sequence name="Hook" from={SCENES.hook.from} durationInFrames={len("hook", 0)}>
      <K1Hook len={len("hook", 0)} />
    </Sequence>
    <Sequence
      name="Perfumy"
      from={SCENES.perfumes.from - 4}
      durationInFrames={len("perfumes", 0) + 4}
    >
      <K2Perfumes len={len("perfumes", 0) + 4} />
    </Sequence>
    <Sequence name="AirPods" from={SCENES.airpods.from} durationInFrames={len("airpods", 0)}>
      <K3AirPods len={len("airpods", 0)} />
    </Sequence>
    <Sequence name="Montage" from={SCENES.montage.from} durationInFrames={len("montage", 0)}>
      <K4Montage len={len("montage", 0)} start={SCENES.montage.from} />
    </Sequence>
    <Sequence name="Proof" from={SCENES.bento.from} durationInFrames={len("bento", 0)}>
      <K5Proof len={len("bento", 0)} start={SCENES.bento.from} />
    </Sequence>
    <Sequence name="Outro" from={SCENES.outro.from} durationInFrames={len("outro")}>
      <K6Outro start={SCENES.outro.from} />
    </Sequence>
    <Finish />
    <Mix hits={HITS} music={music} />
  </AbsoluteFill>
);
