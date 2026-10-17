"""Generate original muted UI taps and a soft launch chime with the standard library."""
from pathlib import Path
import math
import random
import struct
import wave

HERE = Path(__file__).resolve()
TARGET = (HERE.parent / "app/src/main/res/raw" if (HERE.parent / "app").is_dir()
          else HERE.parents[1] / "dolphin/Source/Android/app/src/main/res/raw")
RATE = 44100


def soft_tap(filename, duration, pitch, peak_level):
    """A short, rounded body without airy noise or a frequency sweep."""
    samples, envelopes = [], []
    for i in range(round(RATE * duration)):
        t = i / RATE
        attack = 1 - math.exp(-t / 0.0025)
        decay = math.exp(-t / (duration * 0.27))
        tail = min(1.0, (duration - t) / 0.010)
        envelope = attack * decay * (0.5 - 0.5 * math.cos(math.pi * tail))
        phase = 2 * math.pi * pitch * t
        body = math.sin(phase) + 0.07 * math.sin(2 * phase) + 0.02 * math.sin(3 * phase)
        samples.append(envelope * body)
        envelopes.append(envelope)
    # Remove DC without introducing steps at either end of the waveform.
    bias = sum(samples) / sum(envelopes)
    samples = [value - bias * env for value, env in zip(samples, envelopes)]
    peak = max(abs(v) for v in samples)
    pcm = b"".join(struct.pack("<h", round(v / peak * peak_level * 32767)) for v in samples)
    with wave.open(str(TARGET / filename), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes(pcm)
    print(filename, f"{duration * 1000:.0f} ms soft tap", f"{len(pcm)} PCM bytes")

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
    soft_tap("portal_menu_swish.wav", 0.050, 180, 0.18)
    soft_tap("portal_menu_confirm.wav", 0.065, 230, 0.22)
    launch_chime()
