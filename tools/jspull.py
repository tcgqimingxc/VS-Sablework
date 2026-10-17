#!/usr/bin/env python3
"""Pull a GitHub repo's TEXT files through the jsDelivr CDN (github.com is hosts-blocked here).

Usage:
  python jspull.py <owner>/<repo> <ref> <dest> [--threads 8] [--ext .java,.kt] [--list-only]

Writes <dest>/<full/repo/path> and <dest>/../_trees/<repo>.txt with the full listing.
"""
import concurrent.futures as cf
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

UA = {"User-Agent": "Mozilla/5.0 (jspull)"}
DEFAULT_EXT = (
    ".java", ".kt", ".kts", ".gradle", ".groovy", ".json", ".toml", ".md", ".txt",
    ".properties", ".yml", ".yaml", ".cfg", ".rs", ".glsl", ".vsh", ".fsh", ".frag",
    ".vert", ".comp", ".geom", ".accesswidener", ".at", ".csv", ".bat", ".sh",
    ".py", ".mcmeta", ".gitignore", ".gitattributes", ".gitmodules", ".xml", ".html",
)


def fetch(url, retries=3):
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(1.5 * (i + 1))
    raise last


def get_tree(repo, ref):
    url = f"https://data.jsdelivr.com/v1/packages/gh/{repo}@{urllib.parse.quote(ref, safe='')}?structure=flat"
    data = json.loads(fetch(url).decode("utf-8"))
    files = []
    for f in data.get("files", []):
        files.append((f["name"].lstrip("/"), f.get("size", 0)))
    return files, data.get("version")


def main(argv):
    repo, ref, dest = argv[1], argv[2], argv[3]
    rest = argv[4:]
    threads = 8
    exts = DEFAULT_EXT
    list_only = "--list-only" in rest
    if "--threads" in rest:
        threads = int(rest[rest.index("--threads") + 1])
    if "--ext" in rest:
        exts = tuple(rest[rest.index("--ext") + 1].split(","))
    files, version = get_tree(repo, ref)
    tree_dir = os.path.join(os.path.dirname(os.path.abspath(dest)), "_trees")
    os.makedirs(tree_dir, exist_ok=True)
    tree_file = os.path.join(tree_dir, repo.split("/")[-1] + ".txt")
    with open(tree_file, "w", encoding="utf-8") as fh:
        for p, s in files:
            fh.write(f"{s:>10}  {p}\n")
    selected = [(p, s) for p, s in files if p.lower().endswith(exts)]
    total = sum(s for _, s in selected)
    print(f"[{repo}@{ref}] version={version} all_files={len(files)} selected={len(selected)} ({total/1048576:.1f} MB)", flush=True)
    print(f"  tree listing -> {tree_file}", flush=True)
    if list_only:
        return 0

    base = f"https://cdn.jsdelivr.net/gh/{repo}@{ref}"
    t0 = time.time()
    done = {"n": 0, "bytes": 0, "fail": 0}

    def worker(item):
        path, size = item
        target = os.path.join(dest, path)
        if os.path.exists(target) and os.path.getsize(target) == size and size > 0:
            done["n"] += 1
            done["bytes"] += size
            return None
        os.makedirs(os.path.dirname(target), exist_ok=True)
        try:
            blob = fetch(f"{base}/{urllib.parse.quote(path)}")
        except Exception as e:  # noqa: BLE001
            done["fail"] += 1
            return f"FAIL {path}: {type(e).__name__} {str(e)[:60]}"
        with open(target, "wb") as f:
            f.write(blob)
        done["n"] += 1
        done["bytes"] += len(blob)
        if done["n"] % 100 == 0:
            dt = time.time() - t0
            print(f"  {done['n']}/{len(selected)} files, {done['bytes']/1048576:.1f} MB, {done['bytes']/1048576/max(dt,0.001):.2f} MB/s", flush=True)
        return None

    fails = []
    with cf.ThreadPoolExecutor(max_workers=threads) as ex:
        for res in ex.map(worker, selected):
            if res:
                fails.append(res)
    dt = time.time() - t0
    print(f"[{repo}@{ref}] pulled {done['n']} files ({done['bytes']/1048576:.1f} MB) in {dt:.0f}s = {done['bytes']/1048576/max(dt,0.001):.2f} MB/s, failures={done['fail']}", flush=True)
    for f in fails[:15]:
        print("  " + f, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
