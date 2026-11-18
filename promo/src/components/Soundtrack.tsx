// Voice-over is the backbone. Music sits under it and ducks while anyone speaks; SFX land 2–3 frames
// before the visual hit they belong to. Every cue below has a visual reason (see comments).
import React from "react";
import { Audio, interpolate, Sequence, staticFile } from "remotion";
import { FPS, s, SEGMENTS } from "../utils/timing";

const speaking = (frame: number) => {
  const t = frame / FPS;
  // distance (s) to the nearest speech; 0 inside a segment
  let d = Infinity;
  for (const g of SEGMENTS) {
    if (t >= g.from && t <= g.to) return 0;
    d = Math.min(d, Math.abs(t - g.from), Math.abs(t - g.to));
  }
  return d;
};

const musicVolume = (f: number) => {
  const d = speaking(f);
  const duck = interpolate(d, [0, 0.35], [0.18, 0.42], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const fadeIn = interpolate(f, [0, 20], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const tail = interpolate(f, [s(75.8), s(76.6)], [1, 1.5], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }); // let the end breathe
  return duck * fadeIn * tail;
};

type Cue = { at: number; sfx: string; vol?: number };
const CUES: Cue[] = [
  // hook
  { at: 0.55, sfx: "swipe", vol: 0.35 }, { at: 1.45, sfx: "swipe", vol: 0.3 }, { at: 2.2, sfx: "swipe", vol: 0.28 },
  { at: 5.3, sfx: "whoosh", vol: 0.4 },
  { at: 6.0, sfx: "click", vol: 0.55 }, // camera shutter on the whiteboard
  { at: 7.3, sfx: "whoosh", vol: 0.3 }, // into the gallery
  { at: 11.2, sfx: "whoosh", vol: 0.4 },
  { at: 14.3, sfx: "pop", vol: 0.45 }, // the "?"
  // reveal
  { at: 15.85, sfx: "whoosh", vol: 0.5 }, // white page wipes up
  { at: 16.2, sfx: "thump", vol: 0.6 }, // the mark lands
  { at: 19.2, sfx: "chime", vol: 0.35 }, // "দিনরেখা"
  // product
  { at: 20.0, sfx: "whoosh", vol: 0.35 }, { at: 21.0, sfx: "click", vol: 0.35 },
  { at: 22.2, sfx: "swipe", vol: 0.25 }, { at: 23.5, sfx: "swipe", vol: 0.25 }, { at: 24.6, sfx: "swipe", vol: 0.25 }, { at: 25.6, sfx: "swipe", vol: 0.25 },
  { at: 26.4, sfx: "whoosh", vol: 0.35 }, { at: 27.6, sfx: "click", vol: 0.35 },
  { at: 29.95, sfx: "click", vol: 0.5 }, // second shutter, in the study story
  { at: 31.3, sfx: "whoosh", vol: 0.4 }, // photo flies into the app
  { at: 31.95, sfx: "pop", vol: 0.3 },
  { at: 38.5, sfx: "whoosh", vol: 0.35 }, { at: 40.1, sfx: "click", vol: 0.3 }, { at: 40.9, sfx: "swipe", vol: 0.25 },
  { at: 42.4, sfx: "click", vol: 0.3 }, { at: 43.3, sfx: "swipe", vol: 0.25 },
  { at: 44.2, sfx: "whoosh", vol: 0.35 }, { at: 45.4, sfx: "click", vol: 0.35 },
  { at: 49.9, sfx: "whoosh", vol: 0.3 },
  { at: 52.85, sfx: "lock", vol: 0.7 }, // limit reached → blocked
  { at: 54.45, sfx: "whoosh", vol: 0.35 }, { at: 55.1, sfx: "click", vol: 0.35 },
  { at: 57.1, sfx: "whoosh", vol: 0.35 },
  { at: 59.4, sfx: "swipe", vol: 0.25 }, { at: 60.5, sfx: "swipe", vol: 0.25 }, { at: 61.2, sfx: "swipe", vol: 0.25 }, { at: 62.5, sfx: "swipe", vol: 0.25 },
  { at: 63.7, sfx: "whoosh", vol: 0.35 },
  { at: 66.35, sfx: "click", vol: 0.3 },
  { at: 67.95, sfx: "pop", vol: 0.4 }, { at: 68.95, sfx: "pop", vol: 0.4 }, { at: 69.75, sfx: "pop", vol: 0.4 },
  { at: 70.7, sfx: "whoosh", vol: 0.4 }, { at: 70.95, sfx: "thump", vol: 0.5 },
  { at: 75.85, sfx: "chime", vol: 0.3 }, // final tagline
];

// Counter ticks while the reels count up to the example limit (50.2 s → 52.7 s).
const TICKS = Array.from({ length: 19 }, (_, i) => 50.3 + i * 0.13);

export const Soundtrack: React.FC = () => (
  <>
    <Audio src={staticFile("audio/voiceover.wav")} volume={1} />
    <Audio src={staticFile("music/bed.wav")} volume={musicVolume} />
    {CUES.map((c, i) => (
      <Sequence key={i} from={Math.max(0, s(c.at) - 2)} durationInFrames={60} layout="none">
        <Audio src={staticFile(`sfx/${c.sfx}.wav`)} volume={c.vol ?? 0.4} />
      </Sequence>
    ))}
    {TICKS.map((t, i) => (
      <Sequence key={`t${i}`} from={s(t)} durationInFrames={6} layout="none">
        <Audio src={staticFile("sfx/tick.wav")} volume={0.22} />
      </Sequence>
    ))}
  </>
);
