// 15.9 s → end: the clean white product story, scene by scene, cut on the spoken words.
import React from "react";
import { AbsoluteFill, interpolate, Sequence, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { theme } from "../styles/theme";
import { s, SCENES } from "../utils/timing";
import { Grade, Grain, LightBg, SceneShell, Vignette } from "../components/Layers";
import { Headline, Kicker } from "../components/Text";
import { PhoneMockup } from "../components/PhoneMockup";
import { LogoMark, Wordmark } from "../components/Logo";
import { BlankPhone, ReelFeed, Whiteboard, Shutter, toBn } from "../components/Illustrations";

type Key = keyof typeof SCENES;
const dur = (k: Key) => s(SCENES[k][1]) - s(SCENES[k][0]);
/** frame of an absolute time (s) inside scene k */
const at = (k: Key, sec: number) => s(sec) - s(SCENES[k][0]);

const Light: React.FC<{ k: Key; children: React.ReactNode }> = ({ k, children }) => (
  <SceneShell dur={dur(k)}>
    <LightBg />
    {children}
    <Grade /><Grain /><Vignette />
  </SceneShell>
);

const PhoneStage: React.FC<{ children: React.ReactNode; top?: number }> = ({ children, top = theme.layout.phoneTop }) => (
  <AbsoluteFill style={{ alignItems: "center", top }}>{children}</AbsoluteFill>
);

// ------------------------------------------------------------------ logo reveal (15.9–20.2)
const LogoScene: React.FC = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  // the white page wipes up over the dark problems, then the mark draws itself
  const wipe = interpolate(f, [0, 14], [1, 0], { easing: theme.ease.out, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const name = at("logo", 19.3); // "দিনরেখা" is spoken here
  const lift = spring({ frame: f - (name + 14), fps, config: theme.spring.smooth });
  return (
    <SceneShell dur={dur("logo")} enter={1} exit={10}>
      <LightBg />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", transform: `translateY(${-lift * 40}px)` }}>
        <LogoMark size={300} at={10} />
        <div style={{ height: 40 }} />
        <Wordmark at={name - 4} size={120} />
      </AbsoluteFill>
      <AbsoluteFill style={{ background: theme.colors.night, transform: `translateY(${-(1 - wipe) * 100}%)` }} />
      <Grade /><Grain /><Vignette />
    </SceneShell>
  );
};

// ------------------------------------------------------------------ today & calendar (20.2–26.6)
const TodayScene: React.FC = () => (
  <Light k="today">
    <Headline text="তোমার পুরো দিন, এক জায়গায়।" at={4} size={70} accent="পুরো" />
    <PhoneStage>
      <PhoneMockup tilt={-10} steps={[
        { src: "01-today", at: 0, highlight: { x: 4, y: 64, w: 92, h: 30 }, highlightAt: at("today", 21.0) },
        { src: "19-habits-routines", at: at("today", 22.2) },
        { src: "20-notifications", at: at("today", 23.5) },
        { src: "18-schedule", at: at("today", 24.6) },
        { src: "02-calendar", at: at("today", 25.6) },
      ]} />
    </PhoneStage>
  </Light>
);

/** The feature being spoken, as one pill beside the phone (changes with the narration). */
const FeatureTicker: React.FC<{ k: Key; items: [string, number][] }> = ({ k, items }) => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  return (
    <>
      {items.map(([label, t], i) => {
        const start = at(k, t);
        const end = i < items.length - 1 ? at(k, items[i + 1][1]) : dur(k);
        if (f < start - 2 || f > end + 2) return null;
        const p = spring({ frame: f - start, fps, config: theme.spring.snappy });
        const o = interpolate(f, [end - 6, end], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
        return (
          <div key={label} style={{ position: "absolute", left: 0, right: 0, top: 1395, display: "flex", justifyContent: "center", opacity: o }}>
            <div style={{ padding: "12px 30px", borderRadius: 40, background: theme.colors.ink, color: "#fff", fontFamily: theme.fonts.bn,
              fontWeight: 600, fontSize: 36, opacity: p, transform: `translateY(${(1 - p) * 18}px) scale(${interpolate(p, [0, 1], [0.9, 1])})` }}>
              {label}
            </div>
          </div>
        );
      })}
    </>
  );
};

// ------------------------------------------------------------------ study (26.6–38.7)
const StudyScene: React.FC = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const snapAt = at("study", 29.2); // "আর ক্লাসের বোর্ড বা স্লাইডের ছবি"
  const intoApp = at("study", 31.4); // "সরাসরি কোর্স অনুযায়ী সেভ…"
  const board = spring({ frame: f - snapAt, fps, config: theme.spring.smooth });
  const boardOut = interpolate(f, [intoApp - 4, intoApp + 10], [0, 1], { easing: theme.ease.inOut, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const showBoard = f >= snapAt - 2 && boardOut < 1;
  const phoneDim = f >= snapAt && f < intoApp ? interpolate(f, [snapAt, snapAt + 8], [1, 0.0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }) : 1;
  return (
    <Light k="study">
      <Headline text="পড়ার সময়, শুধু পড়া।" at={4} until={snapAt} size={76} accent="পড়া।" />
      <Sequence from={snapAt} layout="none">
        <Headline text="ক্লাসের ছবি হারিয়ে যাবে না।" at={2} until={at("study", 34.8) - snapAt} size={74} accent="হারিয়ে" />
      </Sequence>
      <Sequence from={at("study", 34.8)} layout="none">
        <Headline text="Course অনুযায়ী সব নোট, এক জায়গায়।" at={2} size={64} accent="নোট," />
      </Sequence>
      <PhoneStage>
        <div style={{ opacity: phoneDim }}>
          <PhoneMockup tilt={10} steps={[
            { src: "03-study-focus", at: 0, highlight: { x: 9, y: 60, w: 82, h: 6.5 }, highlightAt: at("study", 27.6) },
            { src: "04-study-notes", at: intoApp, highlight: { x: 4.5, y: 45, w: 91, h: 19 }, highlightAt: intoApp + 16 },
            { src: "04-study-notes", at: at("study", 34.8), highlight: { x: 53, y: 81, w: 41, h: 6 }, highlightAt: at("study", 35.6) },
          ]} />
        </div>
      </PhoneStage>
      {showBoard ? (
        <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", top: 120, opacity: board * (1 - boardOut),
          transform: `translateY(${boardOut * 220}px) scale(${interpolate(board, [0, 1], [1.06, 1]) * (1 - boardOut * 0.55)})` }}>
          <Whiteboard at={snapAt + 2} />
          <Shutter at={snapAt + 22} />
        </AbsoluteFill>
      ) : null}
    </Light>
  );
};

// ------------------------------------------------------------------ wallet (38.7–44.4)
const WalletScene: React.FC = () => (
  <Light k="wallet">
    <Headline text="আয়, খরচ ও বাজেটের সম্পূর্ণ হিসাব।" at={4} size={66} accent="হিসাব।" />
    <PhoneStage>
      <PhoneMockup tilt={-10} steps={[
        { src: "05-wallet", at: 0, highlight: { x: 9, y: 26, w: 82, h: 7 }, highlightAt: at("wallet", 40.1) },
        { src: "16-budgets", at: at("wallet", 40.9) },
        { src: "05-wallet", at: at("wallet", 41.6), highlight: { x: 3, y: 71, w: 94, h: 19 }, highlightAt: at("wallet", 42.2) },
        { src: "15-wallet-report", at: at("wallet", 43.3) },
      ]} />
    </PhoneStage>
  </Light>
);

// ------------------------------------------------------------------ app blocker (44.4–57.3)
const BlockerScene: React.FC = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const reelsFrom = at("blocker", 50.0); // "ধরো, দিনে মাত্র ৫০টি রিলস"
  const blockAt = at("blocker", 52.85); // "লিমিট শেষ হলে অ্যাপ ব্লক"
  const backAt = at("blocker", 54.5); // "সঙ্গে হার্ড মোড এবং ইমার্জেন্সি আনলক"
  const inReels = f >= reelsFrom - 6 && f < backAt + 6;
  const reelP = interpolate(f, [reelsFrom - 6, reelsFrom + 6, backAt - 4, backAt + 6], [0, 1, 1, 0], { easing: theme.ease.inOut, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const lock = spring({ frame: f - blockAt, fps, config: theme.spring.bouncy });
  const blur = interpolate(f, [blockAt, blockAt + 8], [0, 14], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <Light k="blocker">
      {/* 1 — the real App limits screen: reels & shorts counter */}
      <Headline text="Reels, Shorts — দৈনিক সীমা তুমিই ঠিক করো।" at={4} until={reelsFrom} size={62} accent="সীমা" />
      <PhoneStage>
        <div style={{ opacity: 1 - reelP }}>
          <PhoneMockup tilt={10} steps={[
            { src: "13-blocker", at: 0, highlight: { x: 4, y: 55, w: 92, h: 13.5 }, highlightAt: at("blocker", 45.4) },
            { src: "13-blocker", at: backAt, zoom: 1.12, focusY: 40, highlight: { x: 50, y: 82.6, w: 44, h: 4.6 }, highlightAt: backAt + 18 },
          ]} />
        </div>
      </PhoneStage>
      {/* 2 — example: a limit of 50 reels, counting up, then the block */}
      {inReels ? (
        <AbsoluteFill style={{ alignItems: "center", top: theme.layout.phoneTop, opacity: reelP }}>
          <div style={{ filter: `blur(${blur}px)` }}>
            <BlankPhone width={470}>
              <ReelFeed counter={{ from: 31, to: 50, at: reelsFrom + 4, dur: blockAt - reelsFrom - 8 }} frozen={f >= blockAt} />
            </BlankPhone>
          </div>
          {f >= blockAt ? (
            <div style={{ position: "absolute", top: 330, display: "flex", flexDirection: "column", alignItems: "center", opacity: lock,
              transform: `scale(${interpolate(lock, [0, 1], [1.4, 1])})` }}>
              <svg width="170" height="170" viewBox="0 0 24 24">
                <rect x="4" y="10.5" width="16" height="11" rx="3" fill={theme.colors.ink} />
                <path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" stroke={theme.colors.ink} strokeWidth="2.2" fill="none" strokeLinecap="round" />
                <circle cx="12" cy="16" r="1.6" fill="#fff" />
              </svg>
              <div style={{ marginTop: 18, padding: "14px 34px", borderRadius: 40, background: "#fff", fontFamily: theme.fonts.bn, fontWeight: 700,
                fontSize: 44, color: theme.colors.ink, boxShadow: "0 20px 40px -16px rgba(0,0,0,0.4)" }}>লিমিট শেষ, অ্যাপ বন্ধ</div>
            </div>
          ) : null}
        </AbsoluteFill>
      ) : null}
      <Sequence from={reelsFrom} durationInFrames={backAt - reelsFrom} layout="none">
        <Headline text="প্রতিদিন ৫০টি Reels-এর সীমা সেট করো।" at={2} size={64} accent="৫০টি" />
        <Kicker text="উদাহরণ — সীমা কত হবে, তুমিই ঠিক করো" at={12} top={362} size={30} />
      </Sequence>
      <Sequence from={backAt} layout="none">
        <Headline text="সময় শেষ? এবার নিজের কাজে ফিরে যাও।" at={2} size={64} accent="কাজে" />
      </Sequence>
    </Light>
  );
};

// ------------------------------------------------------------------ progress (57.3–63.9)
const ProgressScene: React.FC = () => (
  <Light k="progress">
    <Headline text="প্রতিদিনের ছোট অগ্রগতি, ৩০ দিনের পরিবর্তন।" at={4} size={62} accent="৩০" />
    <PhoneStage>
      <PhoneMockup tilt={-10} steps={[
        { src: "09-progress-plan", at: 0 },
        { src: "07-progress-today", at: at("progress", 59.4) },
        { src: "10-progress-habits", at: at("progress", 60.5) },
        { src: "12-sleep", at: at("progress", 61.2) },
        { src: "14-screen-time", at: at("progress", 62.5) },
      ]} />
    </PhoneStage>
  </Light>
);

// ------------------------------------------------------------------ privacy (63.9–70.9)
const PrivacyScene: React.FC = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const chips = [["কোনো অ্যাকাউন্ট নেই", 68.0], ["বিজ্ঞাপন নেই", 69.0], ["ট্র্যাকিং নেই", 69.8]] as const;
  return (
    <Light k="privacy">
      <Headline text="সব ডেটা থাকে তোমার ফোনেই।" at={at("privacy", 65.5)} size={66} accent="ফোনেই।" />
      <Kicker text="Offline · তোমার ফোনেই সংরক্ষিত" at={at("privacy", 65.9)} top={300} size={34} />
      <PhoneStage top={theme.layout.phoneTop + 20}>
        <PhoneMockup width={380} tilt={8} steps={[{ src: "22-privacy", at: 0, highlight: { x: 4, y: 14, w: 92, h: 32 }, highlightAt: at("privacy", 66.4) }]} />
      </PhoneStage>
      <div style={{ position: "absolute", left: 0, right: 0, top: 1290, display: "flex", justifyContent: "center", gap: 14, flexWrap: "wrap", padding: "0 60px" }}>
        {chips.map(([label, t], i) => {
          const p = spring({ frame: f - at("privacy", t), fps, config: theme.spring.bouncy });
          return (
            <div key={i} style={{ display: "flex", alignItems: "center", gap: 10, padding: "14px 24px", borderRadius: 40, background: "#fff",
              border: `2px solid ${theme.colors.line}`, fontFamily: theme.fonts.bn, fontWeight: 600, fontSize: 34, color: theme.colors.ink,
              boxShadow: "0 16px 30px -18px rgba(22,24,29,0.35)", opacity: p, transform: `translateY(${(1 - p) * 30}px) scale(${interpolate(p, [0, 1], [0.8, 1])})` }}>
              <svg width="30" height="30" viewBox="0 0 24 24"><circle cx="12" cy="12" r="11" fill={theme.colors.heroSoft} />
                <path d="M7 12.5l3.2 3.2L17 9" stroke={theme.colors.hero} strokeWidth="2.6" fill="none" strokeLinecap="round" strokeLinejoin="round" /></svg>
              {label}
            </div>
          );
        })}
      </div>
    </Light>
  );
};

// ------------------------------------------------------------------ outro (70.9–78.6)
const OutroScene: React.FC = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const line1 = at("outro", 72.1);
  const tag = at("outro", 75.9);
  const t1 = spring({ frame: f - line1, fps, config: theme.spring.smooth });
  const t2 = spring({ frame: f - tag, fps, config: theme.spring.smooth });
  const swap = interpolate(f, [tag - 8, tag], [0, 1], { easing: theme.ease.in, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <SceneShell dur={dur("outro")} enter={10} exit={1}>
      <LightBg />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", top: -120 }}>
        <LogoMark size={250} at={0} />
        <div style={{ height: 36 }} />
        <Wordmark at={4} size={112} />
      </AbsoluteFill>
      <div style={{ position: "absolute", left: 80, right: 80, top: 1240, textAlign: "center", fontFamily: theme.fonts.bn, fontWeight: 600, fontSize: 46,
        lineHeight: 1.4, color: theme.colors.inkDim, opacity: t1 * (1 - swap), transform: `translateY(${(1 - t1) * 20}px)` }}>
        শুধু সময়ের হিসাব নয়,<br />নিজের জীবনটা গুছিয়ে নেওয়ার শুরু।
      </div>
      <div style={{ position: "absolute", left: 80, right: 80, top: 1240, textAlign: "center", fontFamily: theme.fonts.bn, fontWeight: 700, fontSize: 52,
        lineHeight: 1.4, color: theme.colors.ink, opacity: t2, transform: `translateY(${(1 - t2) * 20}px)` }}>
        একদিনে নয়,<br /><span style={{ color: theme.colors.hero }}>প্রতিদিন একটু একটু করে।</span>
      </div>
      <Grade /><Grain /><Vignette />
    </SceneShell>
  );
};

export const ProductScenes: React.FC = () => {
  const seq = (k: Key, C: React.FC, overlap = 0) => (
    <Sequence key={k} from={s(SCENES[k][0]) - overlap} durationInFrames={dur(k) + overlap}><C /></Sequence>
  );
  return (
    <>
      {seq("logo", LogoScene)}
      {seq("today", TodayScene)}
      {seq("study", StudyScene)}
      {seq("wallet", WalletScene)}
      {seq("blocker", BlockerScene)}
      {seq("progress", ProgressScene)}
      {seq("privacy", PrivacyScene)}
      {seq("outro", OutroScene)}
    </>
  );
};
