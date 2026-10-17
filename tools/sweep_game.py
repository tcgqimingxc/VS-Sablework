import os, zipfile, io, sys

GAME = r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251"
NEEDLE = b"sequenced_assembly"
SKIP_DIRS = {"logs", "crash-reports", "PCL", "downloads", "natives", "1.21.1-NeoForge_21.1.251-natives",
             "resourcepacks", "shaderpacks", "saves", "XaeroWaypoints_BACKUP240807"}
seen = 0
for dp, dn, fn in os.walk(GAME):
    dn[:] = [d for d in dn if d not in SKIP_DIRS]
    for f in fn:
        p = os.path.join(dp, f)
        try:
            sz = os.path.getsize(p)
        except OSError:
            continue
        if sz > 60 * 1024 * 1024:
            continue
        if f.endswith((".jar", ".zip")):
            try:
                z = zipfile.ZipFile(p)
            except Exception:
                continue
            hits = []
            for n in z.namelist():
                if n.endswith("/"):
                    continue
                try:
                    d = z.read(n)
                except Exception:
                    continue
                if n.lower().endswith((".jar", ".zip")):
                    try:
                        nz = zipfile.ZipFile(io.BytesIO(d))
                        for nn in nz.namelist():
                            if nn.endswith("/"):
                                continue
                            try:
                                dd = nz.read(nn)
                            except Exception:
                                continue
                            if NEEDLE in dd:
                                hits.append(f"{n}!{nn}")
                    except Exception:
                        pass
                if NEEDLE in d:
                    hits.append(n)
            if hits:
                print(f"JAR/ZIP {p}")
                for h in hits:
                    print("      ", h)
                seen += len(hits)
        else:
            if f.endswith((".json", ".txt", ".js", ".snbt", ".zs", ".properties", ".toml", ".cfg")):
                try:
                    d = open(p, "rb").read()
                except Exception:
                    continue
                if NEEDLE in d:
                    print(f"FILE    {p}  ({sz} bytes)")
                    seen += 1
print("total hits:", seen)
