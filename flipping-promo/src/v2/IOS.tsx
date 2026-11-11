import React from "react";
import { AbsoluteFill, Sequence } from "remotion";
import { Background, Finish } from "../components/Background";
import { LiquidGlassFilter } from "../components/Glass";
import { Hit, Mix } from "../shared/Mix";
import { BEATS, DURATION, SCENES, s } from "../timing";
import {
  I1Search,
  I2App,
  I3Notifs,
  I4Island,
  IslandIdle,
  QUERY,
  StatusBar,
  TYPE_START,
  TYPE_STEP,
} from "./IOSScenes";
import "../fonts";

const b = (i: number) => s(BEATS[i]);

const APP_FROM = 64;
const SEARCH_LEN = 76;
const APP_LEN = SCENES.bento.from + 6 - APP_FROM;
const NOTIF_LEN = SCENES.outro.from - SCENES.bento.from;
const ISLAND_LEN = DURATION - SCENES.outro.from;

const HITS: Hit[] = [
  ...QUERY.split("").map((_, k) => ({
    at: TYPE_START + k * TYPE_STEP,
    sfx: "key",
    vol: 0.28,
  })),
  { at: b(0), sfx: "thump", vol: 0.35 },
  { at: b(2), sfx: "pop", vol: 0.3 },
  { at: b(2) + 4, sfx: "tick", vol: 0.3 },
  { at: b(2) + 8, sfx: "tick", vol: 0.3 },
  { at: b(3), sfx: "tick", vol: 0.3 },
  { at: SEARCH_LEN - 18, sfx: "tap", vol: 0.5 },
  { at: APP_FROM - 2, sfx: "open", vol: 0.5 },
  { at: SCENES.perfumes.from, sfx: "thump", vol: 0.4 },
  ...[6, 8, 10, 12, 13, 14].map((i) => ({ at: b(i) - 3, sfx: "swish", vol: 0.3 })),
  { at: b(8), sfx: "tick", vol: 0.4 },
  { at: b(12), sfx: "tick", vol: 0.4 },
  ...[8, 12, 13, 14, 15].map((i) => ({ at: b(i), sfx: "thump", vol: 0.32 })),
  { at: SCENES.bento.from - 8, sfx: "whoosh", vol: 0.4 },
  ...[16, 18, 20, 22].map((i) => ({ at: b(i), sfx: "notif", vol: 0.42 })),
  { at: SCENES.outro.from - 36, sfx: "riser", vol: 0.3 },
  { at: SCENES.outro.from - 27, sfx: "swell", vol: 0.28 },
  { at: SCENES.outro.from, sfx: "impact", vol: 0.55 },
  { at: SCENES.outro.from + 3, sfx: "open", vol: 0.45 },
  { at: SCENES.outro.from + 14, sfx: "shimmer", vol: 0.4 },
  { at: b(26), sfx: "pop", vol: 0.4 },
  { at: b(28), sfx: "tap", vol: 0.55 },
];

export const IOS: React.FC<{ music: boolean }> = ({ music }) => (
  <AbsoluteFill style={{ backgroundColor: "#030305" }}>
    <LiquidGlassFilter />
    <Background />
    <Sequence name="Search"  durationInFrames={SEARCH_LEN}>
      <I1Search len={SEARCH_LEN} />
    </Sequence>
    <Sequence name="App" from={APP_FROM} durationInFrames={APP_LEN}>
      <I2App len={APP_LEN} start={APP_FROM} />
    </Sequence>
    <Sequence name="Notifications" from={SCENES.bento.from} durationInFrames={NOTIF_LEN}>
      <I3Notifs len={NOTIF_LEN} start={SCENES.bento.from} />
    </Sequence>
    <Sequence name="Island" from={SCENES.outro.from} durationInFrames={ISLAND_LEN}>
      <I4Island start={SCENES.outro.from} />
    </Sequence>
    <Sequence name="Island idle"  durationInFrames={SCENES.outro.from} layout="none">
      <IslandIdle />
    </Sequence>
    <StatusBar />
    <Finish />
    <Mix hits={HITS} music={music} />
  </AbsoluteFill>
);
