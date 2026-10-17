"""把 DecodeOgg 解出来的裸 PCM(s16le) 算成"这一下听起来像什么"的客观指标。

为什么要它：本机没有 ffmpeg，也没法真的"听"，而挑 LOCK/UNLOCK 音效时必须回答
"这是 50 ms 的金属咔哒，还是 3 秒的气流/嗡鸣"。这里用包络与频谱给出可复核的数字：

  peak@      峰值出现的时间（ms）—— 越靠前越像"一下"
  atk        10%→90% 上升时间（ms）—— 越短越像"咔哒/撞击"
  e100       前 100 ms 能量占比 —— 冲击型音效大部分能量都在开头
  tail       峰值后衰减到峰值的 10% 所需时间（ms）—— 短 = 干脆
  cent       整体频谱质心（Hz）—— 越高越"金属/脆"
  cent0      前 40 ms 的频谱质心（Hz）—— 起音的音色
  zcr        过零率（每秒）—— 噪声型（气流/摩擦）显著高于乐音/低频撞击

用法：python tools/sfx_stats.py <pcm目录或文件...> [--rate N --ch N]
      （DecodeOgg 的 stdout 会给 rate/channels，pcm 文件名与 ogg 同名）
"""
import argparse
import os
import sys

import numpy as np


def load(path, rate, channels):
    raw = np.fromfile(path, dtype="<i2")
    if channels > 1:
        raw = raw.reshape(-1, channels).mean(axis=1)
    return raw.astype(np.float64) / 32768.0, rate


def frame_rms(x, frame, hop):
    if len(x) < frame:
        return np.array([np.sqrt(np.mean(x ** 2))]) if len(x) else np.array([0.0])
    n = 1 + (len(x) - frame) // hop
    idx = np.arange(frame)[None, :] + hop * np.arange(n)[:, None]
    return np.sqrt(np.mean(x[idx] ** 2, axis=1))


def spectral_centroid(x, rate, start_s, dur_s):
    a = int(start_s * rate)
    b = min(len(x), a + int(dur_s * rate))
    seg = x[a:b]
    if len(seg) < 32:
        return float("nan")
    win = np.hanning(len(seg))
    spec = np.abs(np.fft.rfft(seg * win))
    freqs = np.fft.rfftfreq(len(seg), 1.0 / rate)
    total = spec.sum()
    return float((spec * freqs).sum() / total) if total > 0 else float("nan")


def stats(path, rate, channels):
    x, rate = load(path, rate, channels)
    if len(x) == 0:
        return None
    dur = len(x) / rate
    absx = np.abs(x)
    peak = absx.max()
    peak_at = float(np.argmax(absx)) / rate * 1000.0
    # 上升时间：第一个到 10% 峰值的点 → 第一个到 90% 的点
    i10 = int(np.argmax(absx >= 0.10 * peak))
    i90 = int(np.argmax(absx >= 0.90 * peak))
    attack = (i90 - i10) / rate * 1000.0
    energy = x ** 2
    total = energy.sum()
    e100 = float(energy[:int(0.1 * rate)].sum() / total) if total > 0 else float("nan")
    # 尾部：峰值之后包络降到峰值 10% 以下（用 10 ms RMS 包络）
    frame = int(0.01 * rate)
    env = frame_rms(x, frame, frame)
    if len(env):
        peak_frame = int(np.argmax(env))
        thr = 0.10 * env.max()
        after = np.where(env[peak_frame:] < thr)[0]
        tail = float(after[0] * 10.0) if len(after) else float((len(env) - peak_frame) * 10.0)
    else:
        tail = float("nan")
    zc = float(np.count_nonzero(np.diff(np.signbit(x))) / dur)
    return {
        "file": os.path.basename(path),
        "dur_s": dur,
        "peak_at_ms": peak_at,
        "attack_ms": attack,
        "e100": e100,
        "tail_ms": tail,
        "cent_hz": spectral_centroid(x, rate, 0.0, dur),
        "cent0_hz": spectral_centroid(x, rate, 0.0, 0.04),
        "zcr": zc,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--rate", type=int, default=44100)
    ap.add_argument("--ch", type=int, default=1)
    ap.add_argument("--manifest", help="每行 '<pcm 文件名>\\t<采样率>\\t<声道数>'（DecodeOgg 的 stdout 就是这三项）")
    ap.add_argument("--sort", default="dur_s")
    args = ap.parse_args()

    meta = {}
    if args.manifest:
        with open(args.manifest, encoding="utf-8") as fh:
            for line in fh:
                parts = line.strip().split("\t")
                if len(parts) >= 3:
                    meta[parts[0]] = (int(parts[1]), int(parts[2]))

    files = []
    for p in args.paths:
        if os.path.isdir(p):
            files += [os.path.join(p, f) for f in os.listdir(p) if f.lower().endswith(".pcm")]
        else:
            files.append(p)
    rows = []
    for f in sorted(files):
        rate, ch = meta.get(os.path.splitext(os.path.basename(f))[0], (args.rate, args.ch))
        s = stats(f, rate, ch)
        if s:
            rows.append(s)
    rows.sort(key=lambda r: r[args.sort])
    hdr = f"{'file':<24}{'dur_s':>8}{'peak@ms':>9}{'atk_ms':>8}{'e100':>7}{'tail_ms':>9}{'cent_Hz':>9}{'cent0_Hz':>10}{'zcr':>8}"
    print(hdr)
    print("-" * len(hdr))
    for r in rows:
        print(f"{r['file']:<24}{r['dur_s']:8.3f}{r['peak_at_ms']:9.1f}{r['attack_ms']:8.1f}"
              f"{r['e100']:7.2f}{r['tail_ms']:9.0f}{r['cent_hz']:9.0f}{r['cent0_hz']:10.0f}{r['zcr']:8.0f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
