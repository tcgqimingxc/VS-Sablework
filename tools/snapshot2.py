#!/usr/bin/env python3
"""Stream-download + extract a GitHub branch archive via codeload, with progress.

Usage: python snapshot2.py <owner>/<repo> <ref> <destdir>
"""
import os
import sys
import tarfile
import time
import urllib.request

UA = {"User-Agent": "Mozilla/5.0 (snapshot)"}


def main(argv):
    repo, ref, dest = argv[1], argv[2], argv[3]
    url = f"https://codeload.github.com/{repo}/tar.gz/refs/heads/{ref}"
    tmp = f"{dest}.tar.gz"
    os.makedirs(os.path.dirname(os.path.abspath(tmp)), exist_ok=True)
    t0 = time.time()
    print(f"[{repo}@{ref}] GET {url}", flush=True)
    req = urllib.request.Request(url, headers=UA)
    got = 0
    last = 0.0
    with urllib.request.urlopen(req, timeout=300) as r, open(tmp, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
            got += len(chunk)
            if got - last >= (16 << 20):
                last = got
                print(f"  {got/1048576:.1f} MB, {got/1048576/max(time.time()-t0,0.001):.2f} MB/s", flush=True)
    print(f"  downloaded {got/1048576:.1f} MB in {time.time()-t0:.0f}s", flush=True)
    if os.path.isdir(dest):
        import shutil
        shutil.rmtree(dest, ignore_errors=True)
    os.makedirs(dest, exist_ok=True)
    n = 0
    with tarfile.open(tmp, "r:gz") as tf:
        members = tf.getmembers()
        roots = {m.name.split("/")[0] for m in members}
        root = roots.pop() if len(roots) == 1 else None
        for m in members:
            if root and m.name == root:
                continue
            name = m.name[len(root) + 1:] if root and m.name.startswith(root + "/") else m.name
            if not name:
                continue
            target = os.path.join(dest, name)
            if m.isdir():
                os.makedirs(target, exist_ok=True)
                continue
            if m.issym() or m.islnk():
                continue
            os.makedirs(os.path.dirname(target), exist_ok=True)
            src = tf.extractfile(m)
            if src is None:
                continue
            with open(target, "wb") as f:
                while True:
                    chunk = src.read(1 << 20)
                    if not chunk:
                        break
                    f.write(chunk)
            n += 1
    os.remove(tmp)
    print(f"[{repo}@{ref}] extracted {n} files -> {dest} in {time.time()-t0:.0f}s", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
