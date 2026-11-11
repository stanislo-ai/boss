import React from "react";
import { Audio } from "@remotion/media";
import { Sequence, interpolate, staticFile } from "remotion";
import { DURATION } from "../timing";

export type Hit = { at: number; sfx: string; vol: number };

/** Music bed + frame-accurate SFX hits. */
export const Mix: React.FC<{
  hits: Hit[];
  music: boolean;
  musicVol?: number;
}> = ({ hits, music, musicVol = 0.85 }) => (
  <>
    {music ? (
      <Audio
        src={staticFile("audio/cold.wav")}
        volume={(f) =>
          interpolate(f, [DURATION - 10, DURATION], [musicVol, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          })
        }
      />
    ) : null}
    {hits
      .filter((h) => h.at >= 0 && h.at < DURATION)
      .map((h, i) => (
        <Sequence key={i} from={h.at} name={`sfx ${h.sfx}`} layout="none">
          <Audio src={staticFile(`sfx/${h.sfx}.wav`)} volume={h.vol} />
        </Sequence>
      ))}
  </>
);
