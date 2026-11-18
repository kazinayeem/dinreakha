"""Synthesises the promo's music bed and SFX kit as 48 kHz WAVs (deterministic, no downloads).

Music: minimal, warm, ~84 BPM. A quiet, slightly tense pad under the hook (problems), then the
full bed (pad + soft piano-like plucks + light pulse) from the product reveal at 19.4 s.

Run: python3 scripts/synth_audio.py   (writes public/music/ and public/sfx/)
"""
import numpy as np, wave, os

SR = 48000
rng = np.random.default_rng(7)
HERE = os.path.dirname(os.path.abspath(__file__))
PUB = os.path.join(HERE, "..", "public")


def write(path, x, stereo=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = np.clip(x, -1, 1)
    if stereo and x.ndim == 1:
        x = np.stack([x, x], 1)
    data = (x * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(2 if data.ndim == 2 else 1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def t_(sec):
    return np.arange(int(sec * SR)) / SR


def lowpass(x, cutoff):
    # simple one-pole low-pass, applied twice
    a = np.exp(-2 * np.pi * cutoff / SR)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for i in range(len(x)):  # small buffers only
            acc = (1 - a) * x[i] + a * acc
            y[i] = acc
        x = y
    return x


def lp_fast(x, cutoff):
    # FFT brick-ish low-pass with a soft knee, for long buffers
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 / (1 + (f / cutoff) ** 4)
    return np.fft.irfft(X, len(x))


def hz(note):  # MIDI -> Hz
    return 440.0 * 2 ** ((note - 69) / 12)


# ---------------------------------------------------------------- music
BPM = 84
BEAT = 60 / BPM
DUR = 78.0
N = int(DUR * SR)
L = np.zeros(N)
R = np.zeros(N)
REVEAL = 19.4  # "দিনরেখা" is spoken here

# chords: Cmaj9, Am9, Fmaj7#11-ish, G6 (two bars each)
CHORDS = [[48, 55, 59, 62, 64], [45, 52, 55, 59, 62], [41, 48, 52, 57, 60], [43, 50, 55, 57, 62]]


def add(buf, start, sig, gain=1.0):
    s = int(start * SR)
    e = min(N, s + len(sig))
    if e > s:
        buf[s:e] += sig[: e - s] * gain


def pad(notes, dur, bright):
    tt = t_(dur)
    sig = np.zeros_like(tt)
    for n in notes:
        for det in (-0.06, 0.06):
            f = hz(n) * 2 ** (det / 12)
            sig += np.sin(2 * np.pi * f * tt) + 0.18 * np.sin(4 * np.pi * f * tt)
    sig /= len(notes) * 2
    env = np.minimum(1, tt / 1.6) * np.minimum(1, (dur - tt) / 1.4)
    return lp_fast(sig * env, bright)


def pluck(note, dur=1.6):
    tt = t_(dur)
    f = hz(note)
    sig = np.sin(2 * np.pi * f * tt) + 0.35 * np.sin(4 * np.pi * f * tt) + 0.12 * np.sin(6 * np.pi * f * tt)
    env = np.exp(-tt * 3.2) * np.minimum(1, tt / 0.004)
    return sig * env


bar = BEAT * 4
t = 0.0
ci = 0
while t < DUR:
    chord = CHORDS[ci % 4]
    seg = bar * 2
    hook = t < REVEAL - 0.5
    p = pad(chord if not hook else chord[:3], seg + 1.2, 900 if hook else 2200)
    add(L, t, p, 0.16 if hook else 0.22)
    add(R, t, p, 0.16 if hook else 0.22)
    if not hook:
        # soft arpeggio, eighth notes, gently panned
        arp = [chord[2] + 12, chord[3] + 12, chord[4] + 12, chord[3] + 12]
        for k in range(16):
            tk = t + k * BEAT / 2
            if tk >= DUR - 2:
                break
            n = arp[k % 4]
            vel = 0.10 if k % 2 == 0 else 0.065
            pl = pluck(n)
            pan = 0.5 + 0.35 * np.sin(k * 0.9)
            add(L, tk, pl, vel * (1 - pan) * 2)
            add(R, tk, pl, vel * pan * 2)
        # light pulse: sub thump on beats 1 and 3
        for k in (0, 2, 4, 6):
            tk = t + k * BEAT
            tt = t_(0.35)
            th = np.sin(2 * np.pi * (52 + 30 * np.exp(-tt * 30)) * tt) * np.exp(-tt * 9)
            add(L, tk, th, 0.18)
            add(R, tk, th, 0.18)
    else:
        # hook: a muted clock tick on each beat (time slipping away)
        for k in range(8):
            tk = t + k * BEAT
            tt = t_(0.05)
            tick = rng.standard_normal(len(tt)) * np.exp(-tt * 120)
            add(L, tk, tick, 0.035)
            add(R, tk, tick, 0.035)
    t += seg
    ci += 1

# master: fade in / out, gentle normalise
tt = np.arange(N) / SR
fade = np.minimum(1, tt / 0.8) * np.clip((DUR - tt) / 3.5, 0, 1)
L *= fade
R *= fade
peak = max(np.abs(L).max(), np.abs(R).max())
L, R = L / peak * 0.7, R / peak * 0.7
write(os.path.join(PUB, "music", "bed.wav"), np.stack([L, R], 1))

# ---------------------------------------------------------------- sfx
def whoosh(d=0.45, up=True):
    tt = t_(d)
    noise = rng.standard_normal(len(tt))
    env = np.sin(np.pi * tt / d) ** 2
    sig = lp_fast(noise, 2500) * env
    hp = sig - lp_fast(sig, 300)
    return hp / np.abs(hp).max() * 0.6


def click():
    tt = t_(0.06)
    s = np.sin(2 * np.pi * 2200 * tt) * np.exp(-tt * 140) + 0.4 * rng.standard_normal(len(tt)) * np.exp(-tt * 300)
    return s / np.abs(s).max() * 0.5


def pop():
    tt = t_(0.18)
    f = 900 * np.exp(-tt * 18) + 300
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 22)
    return s / np.abs(s).max() * 0.5


def tick():
    tt = t_(0.04)
    s = np.sin(2 * np.pi * 3200 * tt) * np.exp(-tt * 200)
    return s * 0.35


def chime():
    tt = t_(1.2)
    s = sum(np.sin(2 * np.pi * hz(n) * tt) * np.exp(-tt * d) for n, d in ((84, 3.5), (88, 4.5), (91, 5.5)))
    return s / np.abs(s).max() * 0.45


def thump():
    tt = t_(0.7)
    f = 40 + 70 * np.exp(-tt * 14)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 5)
    shimmer = sum(np.sin(2 * np.pi * hz(n) * tt) for n in (72, 79, 84)) * np.exp(-tt * 3) * 0.12
    s = s + shimmer
    return s / np.abs(s).max() * 0.7


def lock():
    a = click()
    tt = t_(0.25)
    b = np.sin(2 * np.pi * 180 * tt) * np.exp(-tt * 25)
    out = np.zeros(int(0.3 * SR))
    out[: len(a)] += a
    s = int(0.05 * SR)
    out[s : s + len(b)] += b * 0.6
    return out / np.abs(out).max() * 0.6


def swipe():
    return whoosh(0.22)


for name, fn in dict(whoosh=whoosh, click=click, pop=pop, tick=tick, chime=chime, thump=thump, lock=lock, swipe=swipe).items():
    write(os.path.join(PUB, "sfx", f"{name}.wav"), fn(), stereo=True)

print("ok")
