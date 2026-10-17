#!/usr/bin/env python3
"""Dump a GitHub repo's file tree via the jsDelivr package API (github is hosts-blocked here).

Usage: python jstree.py <owner>/<repo> <ref> [--max N] [--filter SUBSTR]
"""
import json
import sys
import urllib.parse
import urllib.request

UA = {"User-Agent": "Mozilla/5.0 (jstree)"}


def fetch(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.load(r)


def walk(nodes, prefix, out):
    for n in nodes:
        p = f"{prefix}{n['name']}"
        if n.get("type") == "directory":
            walk(n.get("files", []), p + "/", out)
        else:
            out.append((p, n.get("size", 0)))


def main(argv):
    repo, ref = argv[1], argv[2]
    rest = argv[3:]
    maxn = None
    filt = None
    if "--max" in rest:
        maxn = int(rest[rest.index("--max") + 1])
    if "--filter" in rest:
        filt = rest[rest.index("--filter") + 1]
    ref_enc = urllib.parse.quote(ref, safe="")
    url = f"https://data.jsdelivr.com/v1/packages/gh/{repo}@{ref_enc}?structure=flat"
    data = fetch(url)
    files = []
    if "files" in data and data["files"] and isinstance(data["files"][0], dict) and "name" in data["files"][0]:
        walk(data["files"], "", files)
    else:
        files = [(f["name"], f.get("size", 0)) for f in data.get("files", [])]
    if filt:
        files = [f for f in files if filt in f[0]]
    for p, s in files[: maxn or len(files)]:
        print(f"{s:>10}  {p}")
    print(f"# version={data.get('version')} files={len(files)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
