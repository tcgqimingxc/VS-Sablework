"""读完 ogg 页头/尾的 granule position，算出每个 ogg 的时长（秒）+ 名义码率。

为什么需要它：挑选音效时"这是 0.15 s 的咔哒还是 3 s 的气流"是硬事实，
而本机没有 ffmpeg/ffprobe 可用来听/转码；Vorbis 的时长不需要解码器就能从
最后一个 ogg 页的 granule（采样数）除以采样率得到。

用法：python tools/ogg_duration.py <文件或目录> [...]
"""
import os
import struct
import sys


def ogg_info(path):
    """返回 (采样率, 声道数, 时长秒, 名义码率 kbps)；解析失败返回 None。"""
    size = os.path.getsize(path)
    with open(path, "rb") as fh:
        data = fh.read()

    rate = channels = None
    last_granule = 0
    pos = 0
    n = len(data)
    while pos + 27 <= n:
        if data[pos:pos + 4] != b"OggS":
            # 坏页：向后找下一个页头（同步）
            nxt = data.find(b"OggS", pos + 1)
            if nxt < 0:
                break
            pos = nxt
            continue
        _ver, header_type, granule = struct.unpack_from("<BBq", data, pos + 4)
        seg_count = data[pos + 26]
        seg_table = data[pos + 27:pos + 27 + seg_count]
        body_len = sum(seg_table)
        body_off = pos + 27 + seg_count
        if granule >= 0:
            last_granule = granule
        # 第一个包：vorbis identification header
        if rate is None and body_len >= 30 and header_type & 0x02:
            body = data[body_off:body_off + body_len]
            if body[:7] == b"\x01vorbis":
                channels, rate = struct.unpack_from("<BI", body, 11)[0], struct.unpack_from("<I", body, 12)[0]
        pos = body_off + body_len

    if rate is None:
        return None
    seconds = last_granule / float(rate) if rate else 0.0
    kbps = (size * 8 / seconds / 1000.0) if seconds > 0 else 0.0
    return rate, channels, seconds, kbps


def main(argv):
    targets = []
    for arg in argv:
        if os.path.isdir(arg):
            for root, _dirs, files in os.walk(arg):
                targets += [os.path.join(root, f) for f in files if f.lower().endswith(".ogg")]
        else:
            targets.append(arg)
    targets.sort()
    print(f"{'file':<62} {'sec':>7} {'kHz':>6} {'ch':>3} {'kbps':>6}")
    for path in targets:
        info = ogg_info(path)
        name = os.path.basename(path)
        if info is None:
            print(f"{name:<62} {'??':>7}")
            continue
        rate, channels, seconds, kbps = info
        print(f"{name:<62} {seconds:7.3f} {rate / 1000.0:6.1f} {channels:3d} {kbps:6.1f}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
