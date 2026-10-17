#!/usr/bin/env python3
"""Benchmark download throughput of GitHub mirrors / CDNs by reading a fixed number of bytes."""
import sys
import time
import urllib.request

UA = {"User-Agent": "Mozilla/5.0 (speedtest)"}
LIMIT = 3 << 20  # 3 MB
CANDIDATES = [
    ("direct-codeload", "https://codeload.github.com/ryanhcode/sable/tar.gz/refs/heads/main"),
    ("ghproxy.net", "https://ghproxy.net/https://codeload.github.com/ryanhcode/sable/tar.gz/refs/heads/main"),
    ("gh-proxy.com", "https://gh-proxy.com/https://codeload.github.com/ryanhcode/sable/tar.gz/refs/heads/main"),
    ("ghfast.top", "https://ghfast.top/https://codeload.github.com/ryanhcode/sable/tar.gz/refs/heads/main"),
    ("gh.llkk.cc", "https://gh.llkk.cc/https://codeload.github.com/ryanhcode/sable/tar.gz/refs/heads/main"),
    ("ghfile.geekertao", "https://ghfile.geekertao.top/https://codeload.github.com/ryanhcode/sable/tar.gz/refs/heads/main"),
    ("ghp.ci", "https://ghp.ci/https://codeload.github.com/ryanhcode/sable/tar.gz/refs/heads/main"),
    ("jsdelivr-readme", "https://cdn.jsdelivr.net/gh/ryanhcode/sable@main/README.md"),
    ("fastly-jsdelivr", "https://fastly.jsdelivr.net/gh/ryanhcode/sable@main/README.md"),
    ("gcore-jsdelivr", "https://gcore.jsdelivr.net/gh/ryanhcode/sable@main/README.md"),
]


def bench(name, url):
    t0 = time.time()
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=25) as r:
            got = 0
            while got < LIMIT:
                chunk = r.read(1 << 16)
                if not chunk:
                    break
                got += len(chunk)
                if time.time() - t0 > 20:
                    break
    except Exception as e:  # noqa: BLE001
        print(f"{name:22} FAIL {type(e).__name__}: {str(e)[:70]}", flush=True)
        return
    dt = time.time() - t0
    print(f"{name:22} {got/1048576:6.2f} MB in {dt:5.1f}s = {got/1048576/max(dt,0.001):7.2f} MB/s", flush=True)


if __name__ == "__main__":
    only = sys.argv[1] if len(sys.argv) > 1 else None
    for name, url in CANDIDATES:
        if only and only not in name:
            continue
        bench(name, url)
