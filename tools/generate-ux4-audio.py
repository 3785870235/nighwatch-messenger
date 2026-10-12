"""Original deterministic sound design. No external recordings or music."""
from pathlib import Path
import hashlib
import json
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'assets/game/v2/audio'
OUT.mkdir(parents=True, exist_ok=True)
SR = 44100
rng = np.random.default_rng(41210)
records = []

def noise(n, low=0, high=6000):
    a = rng.normal(0, 1, n)
    f = np.fft.rfftfreq(n, 1 / SR)
    shape = np.exp(-(f / high) ** 4) * (1 - np.exp(-(f / max(1, low)) ** 4))
    a = np.fft.irfft(np.fft.rfft(a) * shape, n)
    return a / max(1e-6, np.std(a))

def bell(t, f, decay=2.5):
    a = np.zeros_like(t)
    valid = t >= 0
    s = t[valid]
    for ratio, gain in [(1, 1), (2.01, .33), (2.73, .16), (4.07, .07)]:
        a[valid] += gain * np.sin(2 * np.pi * f * ratio * s) * np.exp(-s * decay * ratio ** .5)
    a[valid] *= np.minimum(s / .007, 1)
    return a

def echo(a, wet=.2):
    b = a.copy()
    for delay, gain in [(.083, .6), (.139, .4), (.241, .25), (.397, .15)]:
        shift = int(delay * SR)
        if shift < len(a):
            b[shift:] += a[:-shift] * gain * wet
    return b

def save(name, a, peak=.16, loop=False):
    if not loop:
        fade = min(int(.025 * SR), len(a) // 4)
        a[:fade] *= np.linspace(0, 1, fade)
        a[-fade:] *= np.linspace(1, 0, fade)
    a *= peak / max(1e-6, abs(a).max())
    right = a * .88 + np.roll(a, 13) * .12
    stereo = np.column_stack((a, right))
    pcm = (stereo * 32767).astype('<i2').tobytes()
    path = OUT / (name + '.wav')
    with wave.open(str(path), 'wb') as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(SR)
        wav.writeframes(pcm)
    records.append({'name': name, 'seconds': len(a) / SR, 'peak': float(abs(stereo).max()), 'rms': float(np.sqrt(np.mean(stereo ** 2))), 'loop': loop, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})

for name, duration in [('wind', 12), ('embers', 8)]:
    t = np.arange(int(duration * SR)) / SR
    if name == 'wind':
        a = noise(len(t), 40, 420) * (.55 + .22 * np.sin(2*np.pi*t/duration) + .13*np.sin(4*np.pi*t/duration))
        a += .09 * np.sin(2*np.pi*110*t) * (1 + np.sin(2*np.pi*t/duration))
        level = .055
    else:
        a = noise(len(t), 180, 2300) * .05
        for at in rng.uniform(.1, duration-.1, 36):
            d = t-at
            a += noise(len(t), 700, 9000) * np.where((d >= 0) & (d < .07), np.exp(-np.maximum(d, 0)*130), 0) * .18
        level = .045
    # Periodic FFT noise and a short circular seam blend keep looping quiet.
    edge = 200
    seam = (a[:edge] + a[-edge:]) * .5
    a[:edge] = seam
    a[-edge:] = seam
    a[:edge] *= np.linspace(.4, 1, edge)
    a[-edge:] *= np.linspace(1, .4, edge)
    save(name, a, level, True)

for name, duration in [('paper', .9), ('page', .55), ('step', .42), ('seal', .65), ('click', .18),
                       ('bell', 3.8), ('spirit', 1.1), ('wonder', 2.4), ('rune', 1.4),
                       ('open', 2.2), ('close', 1.2), ('success', 3.1), ('question', .9), ('transition', .85)]:
    t = np.arange(int(duration * SR)) / SR
    if name in ('paper', 'page'):
        envelope = np.sin(np.pi * t / duration) ** 1.8
        envelope *= (.45 + .55 * np.sin(t * (39 if name == 'paper' else 55)) ** 2)
        a = noise(len(t), 700, 7000) * envelope + .08 * np.sin(2*np.pi*(190*t+70*t*t)) * envelope
    elif name == 'step':
        a = noise(len(t), 60, 900)*np.exp(-t*24) + .7*np.sin(2*np.pi*76*t)*np.exp(-t*35)
        a += .18*noise(len(t), 1000, 6500)*np.exp(-((t-.06)/.018)**2)
    elif name in ('seal', 'click'):
        a = bell(t, 620 if name == 'click' else 195, 22 if name == 'click' else 8)
        a += .3*noise(len(t), 250, 4200)*np.exp(-t*60)
    elif name in ('bell', 'spirit', 'wonder', 'rune', 'success', 'question'):
        notes = {'bell': [(0, 196)], 'spirit': [(0, 1046.5), (.17, 1318.5), (.33, 1568)],
                 'wonder': [(0, 392), (.28, 587.3), (.61, 880)], 'rune': [(0, 440), (.18, 659.3)],
                 'success': [(0, 261.6), (.25, 329.6), (.5, 392), (.85, 523.25)],
                 'question': [(0, 440), (.18, 415.3)]}[name]
        a = sum(bell(t-start, f, 1.7 if name == 'bell' else 3.2) for start, f in notes)
        a = echo(a, .45)
    elif name in ('open', 'close'):
        env = np.sin(np.pi*t/duration)**.7
        a = noise(len(t), 30, 500)*env*.4 + np.sin(2*np.pi*(74*t+9*t*t))*env*.4
        a += noise(len(t), 800, 2300)*np.sin(t*17)**8*env*.07
        a += .4*bell(t-duration*.74, 150, 8)
        a = echo(a, .5)
    else:
        env = np.sin(np.pi*t/duration)**2
        a = noise(len(t), 500, 3500)*env*.22 + bell(t-.12, 784, 6)*.2
    save(name, a, .14 if name in ('bell','success','open','close') else .095)

(OUT/'manifest.json').write_text(json.dumps({'authoring': 'Original deterministic procedural sound design; seeded filtered noise, physical-style resonances, envelopes and short echoes; not field recordings.', 'sample_rate': SR, 'channels': 2, 'bit_depth': 16, 'sounds': records}, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'assets':len(records),'seconds':sum(r['seconds'] for r in records),'bytes':sum(p.stat().st_size for p in OUT.glob('*.wav'))}))
