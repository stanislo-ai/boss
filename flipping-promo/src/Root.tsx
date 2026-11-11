import React from "react";
import { Composition } from "remotion";
import { Promo, PromoProps } from "./Promo";
import { DURATION, FPS, HEIGHT, WIDTH } from "./timing";
import { Keynote } from "./v1/Keynote";
import { IOS } from "./v2/IOS";

export const RemotionRoot: React.FC = () => (
  <>
    {/* Main TikTok cut: music "Cold" + SFX */}
    <Composition
      id="FlippingPromo"
      component={Promo}
      durationInFrames={DURATION}
      fps={FPS}
      width={WIDTH}
      height={HEIGHT}
      defaultProps={{ music: true, sfx: true } satisfies PromoProps}
    />
    {/* SFX only – add "Cold" from TikTok's sound library on upload */}
    <Composition
      id="FlippingPromoNoMusic"
      component={Promo}
      durationInFrames={DURATION}
      fps={FPS}
      width={WIDTH}
      height={HEIGHT}
      defaultProps={{ music: false, sfx: true } satisfies PromoProps}
    />
    <Composition
      id="V1Keynote"
      component={Keynote}
      durationInFrames={DURATION}
      fps={FPS}
      width={WIDTH}
      height={HEIGHT}
      defaultProps={{ music: true }}
    />
    <Composition
      id="V1KeynoteNoMusic"
      component={Keynote}
      durationInFrames={DURATION}
      fps={FPS}
      width={WIDTH}
      height={HEIGHT}
      defaultProps={{ music: false }}
    />
    <Composition
      id="V2iOS"
      component={IOS}
      durationInFrames={DURATION}
      fps={FPS}
      width={WIDTH}
      height={HEIGHT}
      defaultProps={{ music: true }}
    />
    <Composition
      id="V2iOSNoMusic"
      component={IOS}
      durationInFrames={DURATION}
      fps={FPS}
      width={WIDTH}
      height={HEIGHT}
      defaultProps={{ music: false }}
    />
  </>
);
