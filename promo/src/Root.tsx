import React from "react";
import { Composition } from "remotion";
import { DinrekhaPromo } from "./compositions/DinrekhaPromo";
import { FPS, TOTAL_SEC } from "./utils/timing";

export const RemotionRoot: React.FC = () => (
  <Composition id="DinrekhaPromo" component={DinrekhaPromo} durationInFrames={Math.round(TOTAL_SEC * FPS)} fps={FPS} width={1080} height={1920} />
);
