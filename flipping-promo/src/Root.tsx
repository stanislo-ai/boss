import React from "react";
import { Composition } from "remotion";
import { Promo, PromoProps } from "./Promo";
import { DURATION, FPS, HEIGHT, WIDTH } from "./timing";

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
  </>
);
