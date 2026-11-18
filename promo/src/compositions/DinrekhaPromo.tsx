// The whole film: problem hook (dark) → logo → product story (white) → privacy → brand.
import React from "react";
import { AbsoluteFill, useCurrentFrame } from "remotion";
import { LightBg, NightBg } from "../components/Layers";
import { s } from "../utils/timing";
import { theme } from "../styles/theme";
import { HookScene } from "../scenes/HookScene";
import { ProductScenes } from "../scenes/ProductScenes";
import { Subtitles } from "../components/Text";
import { Soundtrack } from "../components/Soundtrack";
import { SCENES } from "../utils/timing";

export const DinrekhaPromo: React.FC = () => {
  const f = useCurrentFrame();
  return (
  <AbsoluteFill style={{ background: theme.colors.bg }}>
    {/* Fixed backdrop, so scene changes dip to the background instead of double-exposing two screens. */}
    {f < s(SCENES.logo[0]) ? <NightBg /> : <LightBg />}
    <HookScene />
    <ProductScenes />
    <Subtitles darkUntil={SCENES.logo[0]} hideFrom={72.0} />
    <Soundtrack />
  </AbsoluteFill>
  );
};
