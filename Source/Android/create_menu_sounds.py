"""Generate original UI swishes and a soft launch chime with the standard library."""
from pathlib import Path
import math
import random
import struct
import wave

TARGET = Path(__file__).resolve().parents[1] / "dolphin/Source/Android/app/src/main/res/raw"
RATE = 44100


def swish(filename, duration, seed, pitch):
    rng = random.Random(seed)
    filtered = 0.0
    bass = 0.0
    phase = 0.0
    samples = []
    for i in range(round(RATE * duration)):
        t = i / RATE
        u = t / duration
        envelope = math.sin(math.pi * u) ** 2.4
        cutoff = 380 + 1100 * math.sin(math.pi * u) ** 2
        alpha = 1 - math.exp(-2 * math.pi * cutoff / RATE)
        filtered += alpha * (rng.uniform(-1, 1) - filtered)
        bass += 0.012 * (filtered - bass)
        phase += 2 * math.pi * (pitch * (1 - 0.52 * u)) / RATE
        # Soft band-limited air with a small falling, rounded low-frequency body.
        samples.append(envelope * ((filtered - bass) * 1.7 + math.sin(phase) * 0.10))
    peak = max(abs(v) for v in samples)
    pcm = b"".join(struct.pack("<h", round(v / peak * 0.40 * 32767)) for v in samples)
    with wave.open(str(TARGET / filename), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes(pcm)
    print(filename, f"{duration * 1000:.0f} ms", f"{len(pcm)} PCM bytes")


def launch_chime():
    duration = 0.62
    samples = []
    for i in range(round(RATE * duration)):
        t = i / RATE
        value = 0.0
        for frequency, onset, gain in ((523.25, 0.0, 0.65), (783.99, 0.045, 0.35), (1046.5, 0.085, 0.16)):
            elapsed = t - onset
            if elapsed < 0:
                continue
            attack = 1 - math.exp(-elapsed / 0.009)
            decay = math.exp(-elapsed / 0.145)
            tone = math.sin(2 * math.pi * frequency * elapsed)
            tone += 0.10 * math.sin(2 * math.pi * frequency * 2 * elapsed)
            value += gain * attack * decay * tone
        # Raised-cosine tail reaches silence without a cut or click.
        tail = min(1.0, (duration - t) / 0.12)
        samples.append(value * (0.5 - 0.5 * math.cos(math.pi * tail)))
    peak = max(abs(v) for v in samples)
    pcm = b"".join(struct.pack("<h", round(v / peak * 0.34 * 32767)) for v in samples)
    with wave.open(str(TARGET / "portal_menu_enter.wav"), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes(pcm)
    print("portal_menu_enter.wav", "620 ms original soft chime", f"{len(pcm)} PCM bytes")


if __name__ == "__main__":
    TARGET.mkdir(parents=True, exist_ok=True)
    swish("portal_menu_swish.wav", 0.125, 82026, 185)
    swish("portal_menu_confirm.wav", 0.165, 92026, 155)
    launch_chime()
