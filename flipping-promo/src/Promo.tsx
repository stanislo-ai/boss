import React from "react";
import { AbsoluteFill, Sequence } from "remotion";
import { Background, Finish } from "./components/Background";
import { LiquidGlassFilter } from "./components/Glass";
import { Hook } from "./scenes/Hook";
import { Perfumes } from "./scenes/Perfumes";
import { AirPods } from "./scenes/AirPods";
import { Montage } from "./scenes/Montage";
import { Bento } from "./scenes/Bento";
import { Outro } from "./scenes/Outro";
import { Soundtrack } from "./Soundtrack";
import { DURATION, OVERLAP, SCENES } from "./timing";
import "./fonts";

export type PromoProps = { music: boolean; sfx: boolean };

const len = (k: keyof typeof SCENES) =>
  Math.min(SCENES[k].to + OVERLAP, DURATION) - SCENES[k].from;

export const Promo: React.FC<PromoProps> = ({ music, sfx }) => (
  <AbsoluteFill style={{ backgroundColor: "#030305" }}>
    <LiquidGlassFilter />
    <Background />

    <Sequence name="Hook" from={SCENES.hook.from} durationInFrames={len("hook")}>
      <Hook len={len("hook")} />
    </Sequence>
    <Sequence
      name="Perfumy"
      from={SCENES.perfumes.from}
      durationInFrames={len("perfumes")}
    >
      <Perfumes len={len("perfumes")} start={SCENES.perfumes.from} />
    </Sequence>
    <Sequence
      name="AirPods"
      from={SCENES.airpods.from}
      durationInFrames={len("airpods")}
    >
      <AirPods len={len("airpods")} />
    </Sequence>
    <Sequence
      name="Montage"
      from={SCENES.montage.from}
      durationInFrames={len("montage")}
    >
      <Montage len={len("montage")} start={SCENES.montage.from} />
    </Sequence>
    <Sequence
      name="Bento"
      from={SCENES.bento.from}
      durationInFrames={len("bento")}
    >
      <Bento len={len("bento")} />
    </Sequence>
    <Sequence
      name="Outro"
      from={SCENES.outro.from}
      durationInFrames={len("outro")}
    >
      <Outro start={SCENES.outro.from} />
    </Sequence>

    <Finish />
    <Soundtrack music={music} sfx={sfx} />
  </AbsoluteFill>
);
