# Dinrekha promo — production notes

**Final:** `Dinrekha-promo-1080x1920.mp4` — 1080×1920, 30 fps, H.264 High / yuv420p, Rec.709 tags,
AAC 48 kHz stereo 256 kb/s, 78.7 s, loudness −14.0 LUFS (social-platform target), true peak ≤ −1 dBTP.
**Preview:** `Dinrekha-promo-preview-540x960.mp4`. **Subtitles:** `Dinrekha-promo-bn.srt` (27 cues, Bangla).

## Asset inventory

| Path | Type | What it is | Used in |
|---|---|---|---|
| `docs/audio/cartesia_…_Arindam_sonic-3.6_bn-BD….wav` | Voice-over (24 kHz mono, 75.76 s) | The only narration in the project; Bangla, natural tone | Whole film — the editing backbone |
| `docs/screenshots/01-today.png` | Real app screenshot | Today page | Today scene |
| `02-calendar`, `18-schedule`, `19-habits-routines`, `20-notifications` | Real screenshots | Calendar, schedule, habits, notification settings | Today scene (cut on each spoken feature) |
| `03-study-focus`, `04-study-notes` | Real screenshots | Focus timer, class notes library | Study scene |
| `05-wallet`, `15-wallet-report`, `16-budgets` | Real screenshots | Wallet home, monthly report, budgets | Wallet scene |
| `13-blocker` | Real screenshot | App limits: reels counter, rules, Emergency unlock | Blocker scene |
| `07-progress-today`, `09-progress-plan`, `10-progress-habits`, `12-sleep`, `14-screen-time` | Real screenshots | Progress tabs, sleep & mood, screen time | Progress scene |
| `22-privacy` | Real screenshot | Privacy & data (app lock, backup) | Privacy scene |
| `docs/dinrekha-logo.svg` | Brand mark | App icon geometry | Logo reveal, outro (animated from its own paths) |
| `docs/Dinrekha-feature-guide-bn.pdf`, `docs/dinrekha-voiceover-bn.txt` | Docs | Feature list used to check every claim | Script / fact check |
| `promo/public/music/bed.wav`, `promo/public/sfx/*.wav` | Synthesised (scripts/synth_audio.py) | Music bed + 8 SFX; no music/SFX existed in the project | Soundtrack |

Not in the project, so **not used / not invented**: an SRT file (built from the real audio, see below),
classroom or social-media footage (drawn as simple, brand-free illustrations instead), the in-app block
screen and "hard mode" UI (the narration names them; the picture uses the real App limits screen and a
generic lock graphic), the note-capture and course-detail screens.

## Subtitles

No SRT existed. The narration is not the earlier script in `docs/` — it is a different, shorter text — so
the subtitles were made from the audio: pauses detected with ffmpeg `silencedetect`, each phrase
transcribed with local Whisper (large-v3-turbo), then corrected by hand to proper Bangla spelling.
Timings are the phrase boundaries in the recording. Keywords (রিলস, ৫০টি, ফোকাস টাইমার, কোর্স,
ওয়ালেট, ৩০ দিনের, ফোনে, দিনরেখা) are tinted mint; words brighten as they are spoken.

## Editing structure (cut on the narration)

| Time | Scene | Picture | On-screen line |
|---|---|---|---|
| 0–5.3 | Hook 1 | Short-video cards flick faster and faster; clock races an hour | আর কত সময় এভাবে চলে যাবে? |
| 5.3–11.2 | Hook 2 | Whiteboard in a camera frame, shutter flash → gallery of 2,341 photos, a lens searching | ক্লাসে বোর্ডের ছবি তুলেছ… / পরীক্ষার আগে ক্লাসের ছবিটা খুঁজে পাচ্ছ না? |
| 11.2–15.9 | Hook 3 | Small receipts pile up, a "?" lands on "মনে থাকে না?" | মাস শেষে টাকার হিসাব মেলে? |
| 15.9–20.2 | Reveal | White page wipes up; the mark draws itself; wordmark lands on "দিনরেখা" | — |
| 20.2–26.6 | Today | Phone rises; screens switch on each spoken feature (routine → habits → prayer reminders → class schedule → calendar) | তোমার পুরো দিন, এক জায়গায়। |
| 26.6–38.7 | Study | Focus timer (Start highlighted) → whiteboard snap → the photo flies into Class notes → course card and Capture note highlighted | পড়ার সময়, শুধু পড়া। / ক্লাসের ছবি হারিয়ে যাবে না। / Course অনুযায়ী সব নোট, এক জায়গায়। |
| 38.7–44.4 | Wallet | In/Out, budgets, accounts, monthly report | আয়, খরচ ও বাজেটের সম্পূর্ণ হিসাব। |
| 44.4–57.3 | Blocker | Real App limits (reels counter) → example feed counting ৩১→৫০ → blur, lock, "লিমিট শেষ" → back to App limits, Emergency unlock highlighted | Reels, Shorts — দৈনিক সীমা তুমিই ঠিক করো। / প্রতিদিন ৫০টি Reels-এর সীমা সেট করো। (marked "উদাহরণ") / সময় শেষ? এবার নিজের কাজে ফিরে যাও। |
| 57.3–63.9 | Progress | 30-day plan → today's score → habits → sleep & mood → screen time | প্রতিদিনের ছোট অগ্রগতি, ৩০ দিনের পরিবর্তন। |
| 63.9–70.9 | Privacy | Privacy & data screen, app lock highlighted; chips pop on each "নেই" | সব ডেটা থাকে তোমার ফোনেই। |
| 70.9–78.6 | Outro | Logo + wordmark; narration line, then the tagline holds ~2.5 s | একদিনে নয়, প্রতিদিন একটু একটু করে। |

Look: dark, quiet problem scenes → clean off-white product world (the relief). One hero colour (the
app's mint) for emphasis; charcoal type; Hind Siliguri for Bangla, Inter for Latin. Every screenshot is
shown at its true aspect ratio inside a phone frame with soft shadow, gentle 3D settle and slow Ken Burns;
highlights are rings drawn around real controls only. Scenes "dip" through a fixed background (no
double exposures). Everything is frame-based springs/bezier easing — no linear motion.

Sound: voice-over normalised (−16 LUFS, high-pass 70 Hz) and untouched otherwise; music bed (84 BPM,
pad + soft plucks, quiet ticking under the hook, full bed from the reveal) ducks to ~−33 LUFS under
speech and rises to ~−25 LUFS in pauses; SFX (whoosh, swipe, click, pop, tick, chime, thump, lock) only
on visual hits, 2 frames early; final mix loudness-normalised to −14 LUFS.

## Rebuild

```bash
cd promo
npm install
sh scripts/prepare-assets.sh      # copies real assets from docs/, synthesises music + SFX
npm run preview                   # half-size check render → out/
npm run render                    # full-size master → out/dinrekha-promo-raw.mp4
ffmpeg -i out/dinrekha-promo-raw.mp4 -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p \
  -colorspace bt709 -color_primaries bt709 -color_trc bt709 -af "loudnorm=I=-14:TP=-1.0:LRA=9" \
  -c:a aac -b:a 256k -ar 48000 -movflags +faststart output/Dinrekha-promo-1080x1920.mp4
```

Source: `src/styles/theme.ts` (colours, fonts, easings), `src/utils/timing.ts` (narration segments and
scene map — change timing here), `src/components/` (PhoneMockup, Text/Subtitles, Logo, Illustrations,
Layers, Soundtrack), `src/scenes/` (HookScene, ProductScenes), `src/compositions/DinrekhaPromo.tsx`.
