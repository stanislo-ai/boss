import React from "react";
import { Audio } from "@remotion/media";
import { Sequence, interpolate, staticFile } from "remotion";
import { BEATS, DURATION, SCENES, s } from "./timing";
import { TILES } from "./scenes/Bento";

type Hit = { at: number; sfx: string; vol: number };

const b = (i: number) => s(BEATS[i]);

// Sound design, frame-accurate to the cuts and the beat grid.
export const HITS: Hit[] = [
  // Hook
  { at: 0, sfx: "swish", vol: 0.35 },
  { at: b(0), sfx: "impact", vol: 0.55 },
  { at: b(2), sfx: "tick", vol: 0.4 },
  { at: b(2) + 6, sfx: "tick", vol: 0.35 },
  { at: b(3), sfx: "tick", vol: 0.4 },
  // -> Perfumes
  { at: SCENES.perfumes.from - 12, sfx: "whoosh", vol: 0.45 },
  { at: SCENES.perfumes.from + 2, sfx: "pop", vol: 0.3 },
  { at: b(6), sfx: "pop", vol: 0.3 },
  // -> AirPods
  { at: SCENES.airpods.from - 12, sfx: "whoosh", vol: 0.45 },
  { at: SCENES.airpods.from, sfx: "thump", vol: 0.35 },
  { at: SCENES.airpods.from + 12, sfx: "shimmer", vol: 0.22 },
  // Montage – every beat a cut
  { at: b(12) - 6, sfx: "swish", vol: 0.35 },
  { at: b(12), sfx: "thump", vol: 0.4 },
  { at: b(13), sfx: "thump", vol: 0.35 },
  { at: b(14), sfx: "thump", vol: 0.35 },
  { at: b(15), sfx: "thump", vol: 0.4 },
  // Bento
  { at: SCENES.bento.from - 10, sfx: "whoosh", vol: 0.4 },
  { at: SCENES.bento.from, sfx: "impact", vol: 0.45 },
  ...Object.values(TILES).map((t) => ({
    at: SCENES.bento.from + t.at,
    sfx: "tick",
    vol: 0.28,
  })),
  { at: SCENES.bento.from + TILES.legit.at + 6, sfx: "pop", vol: 0.3 },
  // -> Outro
  { at: SCENES.outro.from - 36, sfx: "riser", vol: 0.32 },
  { at: SCENES.outro.from - 27, sfx: "swell", vol: 0.3 },
  { at: SCENES.outro.from, sfx: "impact", vol: 0.6 },
  { at: SCENES.outro.from + 6, sfx: "shimmer", vol: 0.45 },
  { at: b(26), sfx: "pop", vol: 0.4 },
  { at: b(28), sfx: "tap", vol: 0.6 },
];

export const Soundtrack: React.FC<{ music?: boolean; sfx?: boolean }> = ({
  music = true,
  sfx = true,
}) => (
  <>
    {music ? (
      <Audio
        src={staticFile("audio/cold.wav")}
        volume={(f) =>
          interpolate(f, [DURATION - 10, DURATION], [0.9, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          })
        }
      />
    ) : null}
    {sfx
      ? HITS.filter((h) => h.at >= 0).map((h, i) => (
          <Sequence key={i} from={h.at} name={`sfx ${h.sfx}`} layout="none">
            <Audio src={staticFile(`sfx/${h.sfx}.wav`)} volume={h.vol} />
          </Sequence>
        ))
      : null}
  </>
);
