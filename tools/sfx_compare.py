"""对比若干音效的包络/频带分布，回答"这两个声音听起来像不像同一个"。

sfx_stats.py 给的是单文件标量（时长/起音/质心）；挑 LOCK/UNLOCK 时需要的是
成对的可比性：把 50 ms RMS 包络归一化后求相关系数、把能量按倍频程分档求相关系数。
相关系数接近 1 = 同一套模板换了音高，接近 0 = 两种不同的动作。

用法：python tools/sfx_compare.py <pcm目录> --manifest <name\\trate\\tch> [--envelope name1,name2]
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


def envelope(x, rate, win_s=0.05):
    win = max(1, int(win_s * rate))
    n = max(1, len(x) // win)
    trimmed = x[:n * win].reshape(n, win)
    return np.sqrt(np.mean(trimmed ** 2, axis=1))


def band_energy(x, rate, edges=(0, 200, 500, 1000, 2000, 4000, 8000, 16000)):
    if len(x) < 64:
        return np.zeros(len(edges) - 1)
    spec = np.abs(np.fft.rfft(x * np.hanning(len(x)))) ** 2
    freqs = np.fft.rfftfreq(len(x), 1.0 / rate)
    out = []
    for a, b in zip(edges[:-1], edges[1:]):
        out.append(spec[(freqs >= a) & (freqs < b)].sum())
    out = np.array(out, dtype=np.float64)
    total = out.sum()
    return out / total if total > 0 else out


def corr(a, b):
    if a.std() == 0 or b.std() == 0:
        return float("nan")
    return float(np.corrcoef(a, b)[0, 1])


def resample_env(env, n=40):
    """把任意长度的包络重采样到 n 点（短音效只有一两帧时按常数处理），便于两两求相关。"""
    if len(env) == 0:
        return np.zeros(n)
    if len(env) == 1:
        return np.full(n, env[0])
    idx = np.linspace(0, len(env) - 1, n)
    return np.interp(idx, np.arange(len(env)), env)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pcm_dir")
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--envelope", help="只画这几个的包络（逗号分隔，不带 .pcm）")
    ap.add_argument("--first", type=float, help="只取每个文件的前 N 秒做包络/频带对比（长尾近乎静音时会污染相关系数）")
    ap.add_argument("--timeline", help="逐段打印这几个的时间线（dB + 功率质心 + <200Hz 占比），逗号分隔")
    args = ap.parse_args()

    meta = {}
    with open(args.manifest, encoding="utf-8") as fh:
        for line in fh:
            parts = line.strip().split("\t")
            if len(parts) >= 3:
                meta[parts[0]] = (int(parts[1]), int(parts[2]))

    data = {}
    for name, (rate, ch) in sorted(meta.items()):
        path = os.path.join(args.pcm_dir, name + ".pcm")
        if not os.path.exists(path):
            continue
        x, rate = load(path, rate, ch)
        if args.first:
            x = x[:int(args.first * rate)]
        data[name] = (x, rate)

    if args.timeline:
        print(f"=== 时间线（每 100 ms：dB 相对峰值 / 功率质心 Hz / <200 Hz 能量占比）===")
        for name in args.timeline.split(","):
            if name not in data:
                continue
            x, rate = data[name]
            seg = int(0.1 * rate)
            n = max(1, len(x) // seg)
            peak = np.sqrt(np.mean(x ** 2)) if len(x) else 0.0
            print(f"-- {name}（{len(x) / rate:.2f} s）")
            for i in range(n):
                a = x[i * seg:(i + 1) * seg]
                rms = np.sqrt(np.mean(a ** 2))
                db = 20 * np.log10(max(rms, 1e-9) / max(peak, 1e-9)) if peak > 0 else -99
                spec = np.abs(np.fft.rfft(a * np.hanning(len(a)))) ** 2
                freqs = np.fft.rfftfreq(len(a), 1.0 / rate)
                pw = spec.sum()
                cent = float((spec * freqs).sum() / pw) if pw > 0 else float("nan")
                low = float(spec[freqs < 200].sum() / pw) if pw > 0 else float("nan")
                print(f"   {i * 0.1:5.1f}s  {db:7.1f} dB  cent={cent:8.0f} Hz  <200Hz={low * 100:5.1f}%")
        print()

    if args.envelope:
        print("=== 50 ms RMS 包络（dB 相对峰值；每格 200 ms）===")
        for name in args.envelope.split(","):
            if name not in data:
                continue
            x, rate = data[name]
            env = envelope(x, rate, 0.2)
            db = 20 * np.log10(np.maximum(env, 1e-6) / max(env.max(), 1e-6))
            bars = "".join("#" if v > -12 else ("+" if v > -24 else ("." if v > -40 else " ")) for v in db)
            print(f"{name:<22} {bars}")
            print(f"{'':<22} 0 dB=-12 '#' / -24 '+' / -40 '.' ；共 {len(db) * 0.2:.1f} s")
        print()

    names = sorted(data)
    print("=== 包络相关系数（20 段归一化 RMS 包络）===")
    envs = {n: resample_env(envelope(data[n][0], data[n][1])) for n in names}
    print(f"{'':<22}" + "".join(f"{n[:9]:>10}" for n in names))
    for a in names:
        row = "".join(f"{corr(envs[a], envs[b]):10.2f}" for b in names)
        print(f"{a:<22}{row}")
    print()

    print("=== 倍频程能量分布相关系数（0-200-500-1k-2k-4k-8k-16k Hz）===")
    bands = {n: band_energy(data[n][0], data[n][1]) for n in names}
    print(f"{'':<22}" + "".join(f"{n[:9]:>10}" for n in names))
    for a in names:
        row = "".join(f"{corr(bands[a], bands[b]):10.2f}" for b in names)
        print(f"{a:<22}{row}")
    print()

    print("=== 倍频程能量占比（%）===")
    edges = (0, 200, 500, 1000, 2000, 4000, 8000, 16000)
    hdr = "".join(f"{f'{a}-{b}':>10}" for a, b in zip(edges[:-1], edges[1:]))
    print(f"{'file':<22}{hdr}")
    for n in names:
        print(f"{n:<22}" + "".join(f"{v * 100:10.1f}" for v in bands[n]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
