#!/usr/bin/env python3
"""Download + extract a GitHub branch archive via codeload (direct, hosts-file does not block it).

Usage: python snapshot.py <owner>/<repo> <ref> <destdir>
Creates <destdir> containing the repo tree (the archive's single top-level dir is flattened).
"""
import io
import os
import sys
import tarfile
import time
import urllib.request

UA = {"User-Agent": "Mozilla/5.0 (snapshot)"}


def main(argv):
    repo, ref, dest = argv[1], argv[2], argv[3]
    url = f"https://codeload.github.com/{repo}/tar.gz/refs/heads/{ref}"
    os.makedirs(dest, exist_ok=True)
    t0 = time.time()
    print(f"[{repo}@{ref}] GET {url}", flush=True)
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=300) as r:
        blob = r.read()
    print(f"  downloaded {len(blob)/1048576:.1f} MB in {time.time()-t0:.0f}s", flush=True)
    with tarfile.open(fileobj=io.BytesIO(blob), mode="r:gz") as tf:
        members = tf.getmembers()
        # flatten the single top-level directory
        roots = {m.name.split("/")[0] for m in members}
        root = roots.pop() if len(roots) == 1 else None
        n = 0
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
    print(f"[{repo}@{ref}] extracted {n} files -> {dest} in {time.time()-t0:.0f}s", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
