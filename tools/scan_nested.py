import json, os, zipfile, glob, io, sys

def scan_zip(z, label, depth=0, out=None):
    for n in z.namelist():
        low = n.lower()
        if not low.endswith(".json"):
            continue
        if "/recipe/" not in low and "/recipes/" not in low:
            continue
        try:
            raw = z.read(n).decode("utf-8-sig")
        except Exception:
            continue
        if "create:sequenced_assembly" not in raw:
            continue
        try:
            d = json.loads(raw)
        except Exception as e:
            out.append((label, n, "PARSE-FAIL", str(e), None)); continue
        probs = []
        seq = d.get("sequence") if isinstance(d.get("sequence"), list) else []
        if not seq:
            probs.append("sequence empty/missing")
        for i, s in enumerate(seq):
            if not isinstance(s, dict):
                probs.append(f"step{i} not obj"); continue
            st = s.get("type", "?")
            ings = s.get("ingredients")
            if ings is None:
                probs.append(f"step{i}({st}) NO ingredients")
            elif not isinstance(ings, list):
                probs.append(f"step{i}({st}) ingredients not list")
            elif len(ings) == 0:
                probs.append(f"step{i}({st}) EMPTY ingredients <<< CRASH")
        out.append((label, n, d.get("type"), "; ".join(probs) or "ok",
                    json.dumps([[s.get("type"), len(s.get("ingredients") or []) if isinstance(s.get("ingredients"), list) else s.get("ingredients")] for s in seq if isinstance(s, dict)])))
    # nested jars
    for n in z.namelist():
        if n.lower().endswith(".jar"):
            try:
                data = z.read(n)
                nz = zipfile.ZipFile(io.BytesIO(data))
            except Exception:
                continue
            print(f"   [nested jar] {label} -> {n}")
            scan_zip(nz, label + "!" + os.path.basename(n), depth+1, out)

roots = [
    r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods",
    r"C:\Users\yql11\OneDrive\Desktop\.minecraft\mods",
    r"C:\Users\yql11\AppData\Roaming\PrismLauncher\instances\CSN_The Song Of The Sky\minecraft\mods",
    r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs",
]
jars = []
for r in roots:
    if os.path.isdir(r):
        jars += glob.glob(os.path.join(r, "*.jar"))
jars = sorted(set(jars))
print("outer jars:", len(jars))
seen = set()
out = []
for j in jars:
    if os.path.basename(j) in seen:   # dedupe by filename across folders
        continue
    seen.add(os.path.basename(j))
    try:
        z = zipfile.ZipFile(j)
    except Exception:
        continue
    scan_zip(z, os.path.basename(j), 0, out)

print("\n=== nested+outer sequenced_assembly report:", len(out))
bad = [x for x in out if x[3] != "ok"]
print("=== PROBLEM RECIPES:", len(bad))
for x in bad:
    print("  !!", x[0], "::", x[1], "->", x[3], x[4])
print("\n=== non-ok from these jars (all):")
for x in out:
    if x[3] != "ok":
        print("  !!", x[0], "::", x[1], x[3])
