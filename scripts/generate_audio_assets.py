"""Generate small procedural WAV audio assets for the app (no external/downloaded assets,
no copyright concerns): a short looping chiptune-style background track and a short
percussive click for tile moves. Pure stdlib (wave/struct/math) PCM16 mono synthesis.
"""
import math
import struct
import wave

SAMPLE_RATE = 44100


def envelope(n, attack, release, sustain_level=1.0):
    """Linear attack/release envelope over n samples (attack/release in samples)."""
    env = [1.0] * n
    a = min(attack, n // 2)
    r = min(release, n // 2)
    for i in range(a):
        env[i] = i / max(a, 1)
    for i in range(r):
        env[n - 1 - i] = i / max(r, 1)
    return env


def square(freq, duration, volume, duty=0.5):
    n = int(SAMPLE_RATE * duration)
    period = SAMPLE_RATE / freq
    out = []
    for i in range(n):
        phase = (i % period) / period
        out.append(volume if phase < duty else -volume)
    env = envelope(n, int(0.005 * SAMPLE_RATE), int(0.02 * SAMPLE_RATE))
    return [s * e for s, e in zip(out, env)]


def sine(freq, duration, volume):
    n = int(SAMPLE_RATE * duration)
    out = [volume * math.sin(2 * math.pi * freq * i / SAMPLE_RATE) for i in range(n)]
    env = envelope(n, int(0.01 * SAMPLE_RATE), int(0.03 * SAMPLE_RATE))
    return [s * e for s, e in zip(out, env)]


def mix(*tracks):
    length = max(len(t) for t in tracks)
    out = [0.0] * length
    for t in tracks:
        for i, s in enumerate(t):
            out[i] += s
    peak = max(1.0, max(abs(s) for s in out))
    return [s / peak * 0.9 for s in out]


def write_wav(path, samples):
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        frames = b"".join(struct.pack("<h", max(-32767, min(32767, int(s * 32767)))) for s in samples)
        w.writeframes(frames)


def note_track(notes_and_durations, volume, duty=0.5):
    out = []
    for freq, dur in notes_and_durations:
        if freq == 0:
            out.extend([0.0] * int(SAMPLE_RATE * dur))
        else:
            out.extend(square(freq, dur, volume, duty))
    return out


# ---- Background loop: playful major-pentatonic arpeggio + walking bass (chiptune) ----
C4, D4, E4, G4, A4 = 261.63, 293.66, 329.63, 392.00, 440.00
C5, D5, E5, G5 = 523.25, 587.33, 659.25, 783.99
C3, G3 = 130.81, 196.00

STEP = 0.16
lead_pattern = [
    (C5, STEP), (E5, STEP), (G5, STEP), (E5, STEP),
    (D5, STEP), (G5, STEP), (E5, STEP), (C5, STEP),
    (C5, STEP), (D5, STEP), (E5, STEP), (G5, STEP),
    (E5, STEP), (D5, STEP), (C5, STEP), (0, STEP),
]
bass_pattern = [
    (C3, STEP * 4), (G3, STEP * 4),
    (C3, STEP * 4), (G3, STEP * 4),
]

lead = note_track(lead_pattern, volume=0.22, duty=0.4)
bass = note_track(bass_pattern, volume=0.16, duty=0.5)
bg_music = mix(lead, bass)
write_wav("app/src/main/res/raw/bg_music.wav", bg_music)

# ---- Tile move click: quick downward sine sweep with fast decay ("tock") ----
n_click = int(SAMPLE_RATE * 0.09)
click = []
for i in range(n_click):
    t = i / SAMPLE_RATE
    freq = 820 - 4200 * t  # quick downward sweep
    freq = max(freq, 120)
    click.append(math.sin(2 * math.pi * freq * t))
env = envelope(n_click, int(0.002 * SAMPLE_RATE), int(0.07 * SAMPLE_RATE))
click = [s * e * 0.85 for s, e in zip(click, env)]
write_wav("app/src/main/res/raw/sfx_tile_move.wav", click)

print("Generated bg_music.wav and sfx_tile_move.wav")
