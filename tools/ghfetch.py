#!/usr/bin/env python3
"""Fetch GitHub content through a reachable mirror.

github.com is mapped to 127.0.0.1 in this machine's hosts file, so direct
GitHub access fails.  ghproxy.net works as a git/raw mirror.

Usage:
  python ghfetch.py raw <owner>/<repo> <ref> <path...>      # raw.githubusercontent.com file
  python ghfetch.py api <owner>/<repo> [suffix]             # api.github.com/repos/<owner>/<repo>[suffix]
  python ghfetch.py url <full-url>                          # arbitrary URL (direct)
  python ghfetch.py proxyurl <full-url>                     # arbitrary URL via ghproxy prefix
"""
import sys
import urllib.request
import urllib.error

PROXY = "https://ghproxy.net/"
UA = {"User-Agent": "Mozilla/5.0 (ghfetch)"}


def get(url, timeout=60):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = r.read()
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        return data.decode("utf-8", "replace")


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    mode = argv[1]
    if mode == "raw":
        repo, ref, path = argv[2], argv[3], "/".join(argv[4:])
        url = f"{PROXY}https://raw.githubusercontent.com/{repo}/{ref}/{path}"
    elif mode == "api":
        repo = argv[2]
        suffix = argv[3] if len(argv) > 3 else ""
        url = f"{PROXY}https://api.github.com/repos/{repo}{suffix}"
    elif mode == "url":
        url = argv[2]
    elif mode == "proxyurl":
        url = PROXY + argv[2]
    else:
        print(__doc__)
        return 2
    try:
        sys.stdout.write(get(url))
    except urllib.error.HTTPError as e:
        sys.stderr.write(f"HTTPError {e.code} for {url}\n")
        return 1
    except Exception as e:  # noqa: BLE001
        sys.stderr.write(f"{type(e).__name__}: {e} for {url}\n")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
