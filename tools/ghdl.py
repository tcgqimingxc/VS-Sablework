#!/usr/bin/env python3
"""Download a file (optionally through the ghproxy mirror) with a progress trace.

Usage:
  python ghdl.py proxyurl <github-url> <dest>
  python ghdl.py url <url> <dest>
"""
import os
import sys
import time
import urllib.request

PROXY = "https://ghproxy.net/"
UA = {"User-Agent": "Mozilla/5.0 (ghdl)"}


def main(argv):
    mode, url, dest = argv[1], argv[2], argv[3]
    if mode == "proxyurl":
        url = PROXY + url
    os.makedirs(os.path.dirname(os.path.abspath(dest)), exist_ok=True)
    req = urllib.request.Request(url, headers=UA)
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
        total = int(r.headers.get("Content-Length") or 0)
        got = 0
        last = 0
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
            got += len(chunk)
            if got - last >= (8 << 20):
                last = got
                print(f"  {got/1048576:.1f}/{total/1048576:.1f} MB  {got/1048576/max(time.time()-t0,0.001):.2f} MB/s", flush=True)
    print(f"DONE {dest} {os.path.getsize(dest)/1048576:.1f} MB in {time.time()-t0:.0f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
