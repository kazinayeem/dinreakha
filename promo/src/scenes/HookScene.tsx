// 0–15.9 s — three relatable problems, on a dark, quiet backdrop. Fast but controlled.
import React from "react";
import { AbsoluteFill, interpolate, Sequence, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { theme } from "../styles/theme";
import { s, SCENES } from "../utils/timing";
import { Grain, NightBg, SceneShell, Vignette } from "../components/Layers";
import { Headline } from "../components/Text";
import { BlankPhone, FastClock, GalleryGrid, ReelFeed, Receipts, Shutter, Whiteboard } from "../components/Illustrations";

const dur = (k: keyof typeof SCENES) => s(SCENES[k][1]) - s(SCENES[k][0]);

/** Beat 1 — reels flick past faster and faster while the clock races an hour ahead. */
const Reels: React.FC = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const D = dur("hookReels");
  const push = interpolate(f, [0, D], [1, 1.08], { easing: theme.ease.inOut, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const clockP = spring({ frame: f - 10, fps, config: theme.spring.smooth });
  return (
    <SceneShell dur={D} enter={4} exit={6}>
      <NightBg />
      <AbsoluteFill style={{ alignItems: "center", top: theme.layout.phoneTop - 20, transform: `scale(${push})` }}>
        <BlankPhone width={470}><ReelFeed /></BlankPhone>
      </AbsoluteFill>
      <div style={{ position: "absolute", top: 1220, right: 120, opacity: clockP, transform: `translateY(${(1 - clockP) * 20}px)` }}>
        <FastClock startMin={22 * 60 + 5} endMin={23 * 60 + 8} dur={D} />
      </div>
      <Headline text="আর কত সময় এভাবে চলে যাবে?" at={4} size={78} dark accent="সময়" />
      <Grain dark /><Vignette dark />
    </SceneShell>
  );
};

/** Beat 2 — snap the board in class… then the night before the exam, lost in the gallery. */
const ClassPhoto: React.FC = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const D = dur("hookClass");
  const toGallery = s(7.3) - s(SCENES.hookClass[0]); // "কিন্তু পরীক্ষার আগের রাতে…"
  const boardP = spring({ frame: f, fps, config: theme.spring.smooth });
  const boardOut = interpolate(f, [toGallery - 6, toGallery + 4], [0, 1], { easing: theme.ease.in, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <SceneShell dur={D} enter={5} exit={6}>
      <NightBg />
      {/* the class: a whiteboard seen through a phone camera frame */}
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", opacity: 1 - boardOut,
        transform: `scale(${interpolate(boardP, [0, 1], [1.08, 1]) * (1 - boardOut * 0.1)})` }}>
        <Whiteboard at={2} />
        <div style={{ position: "absolute", width: 900, height: 600, border: "3px solid rgba(255,255,255,0.75)", borderRadius: 28 }}>
          {[[0, 0], [1, 0], [0, 1], [1, 1]].map(([x, y], i) => (
            <div key={i} style={{ position: "absolute", width: 60, height: 60, [x ? "right" : "left"]: -3, [y ? "bottom" : "top"]: -3,
              borderColor: "#fff", borderStyle: "solid", borderWidth: `${y ? 0 : 8}px ${x ? 8 : 0}px ${y ? 8 : 0}px ${x ? 0 : 8}px`, borderRadius: 10 }} />
          ))}
        </div>
        <Shutter at={s(6.0) - s(SCENES.hookClass[0])} />
      </AbsoluteFill>
      <Sequence from={toGallery} layout="none">
        <GalleryBeat />
      </Sequence>
      <Headline text="ক্লাসে বোর্ডের ছবি তুলেছ…" at={2} until={toGallery} size={74} dark accent="ছবি" />
      <Sequence from={toGallery} layout="none">
        <Headline text="পরীক্ষার আগে ক্লাসের ছবিটা খুঁজে পাচ্ছ না?" at={2} size={74} dark accent="খুঁজে" />
      </Sequence>
      <Grain dark /><Vignette dark />
    </SceneShell>
  );
};

const GalleryBeat: React.FC = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = spring({ frame: f, fps, config: theme.spring.smooth });
  return (
    <AbsoluteFill style={{ alignItems: "center", top: theme.layout.phoneTop + 10, opacity: p,
      transform: `translateY(${(1 - p) * 90}px) scale(${interpolate(p, [0, 1], [0.94, 1])})` }}>
      <BlankPhone width={470} dark={false}><GalleryGrid at={4} /></BlankPhone>
    </AbsoluteFill>
  );
};

/** Beat 3 — small spends pile up; the month doesn't add up. */
const Money: React.FC = () => {
  const f = useCurrentFrame();
  const D = dur("hookMoney");
  const q = s(14.3) - s(SCENES.hookMoney[0]); // "সেটা কি মনে থাকে না?"
  const sway = Math.sin(f / 25) * 6;
  return (
    <SceneShell dur={D} enter={5} exit={10}>
      <NightBg />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", top: -90, transform: `translateY(${sway}px)` }}>
        <Receipts at={2} />
      </AbsoluteFill>
      <Headline text="মাস শেষে টাকার হিসাব মেলে?" at={3} size={80} dark accent="হিসাব" />
      <QuestionMark at={q} />
      <Grain dark /><Vignette dark />
    </SceneShell>
  );
};

const QuestionMark: React.FC<{ at: number }> = ({ at }) => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = spring({ frame: f - at, fps, config: theme.spring.bouncy });
  return (
    <div style={{ position: "absolute", left: 0, right: 0, top: 1215, textAlign: "center", fontFamily: theme.fonts.latin, fontWeight: 700,
      fontSize: 170, color: theme.colors.sun, opacity: p, transform: `scale(${interpolate(p, [0, 1], [0.4, 1])})` }}>?</div>
  );
};

export const HookScene: React.FC = () => (
  <>
    <Sequence from={s(SCENES.hookReels[0])} durationInFrames={dur("hookReels")}><Reels /></Sequence>
    <Sequence from={s(SCENES.hookClass[0])} durationInFrames={dur("hookClass")}><ClassPhoto /></Sequence>
    <Sequence from={s(SCENES.hookMoney[0])} durationInFrames={dur("hookMoney")}><Money /></Sequence>
  </>
);
