import { Composition } from "remotion";
import { CameraMotionBlur } from "@remotion/motion-blur";
import { Background, FLogoReveal, Vignette } from "./FLogo/FLogoReveal";
import { AbsoluteFill } from "remotion";

export const MyComposition = () => {
  return (
    <Composition
      id="FLogoReveal"
      component={FLogoWithBlur}
      durationInFrames={120}
      fps={60}
      width={1080}
      height={1080}
    />
  );
};

export const FLogoWithBlur: React.FC = () => {
  return (
    <AbsoluteFill>
      <Background />
      <CameraMotionBlur shutterAngle={200} samples={8}>
        <FLogoReveal />
      </CameraMotionBlur>
      <Vignette />
    </AbsoluteFill>
  );
};
