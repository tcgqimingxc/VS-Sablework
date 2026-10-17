import os, glob, hashlib, zipfile, io, json, sys

names = ["vs_clockwork", "create-1.21.1-6.0.10", "create-aeronautics-bundled", "sable-neoforge", "alltheleaks",
         "Jade-1.21.1", "jei-1.21.1-neoforge", "mezz_config", "CrashAssistant"]
roots = [r"C:\Users\yql11\OneDrive\Desktop\.minecraft", r"C:\Users\yql11\AppData\Roaming\PrismLauncher",
         r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork"]
found = {}
for r in roots:
    for dp, dn, fn in os.walk(r):
        for f in fn:
            if not f.endswith(".jar"):
                continue
            for n in names:
                if f.startswith(n):
                    p = os.path.join(dp, f)
                    found.setdefault(f, []).append(p)

for f, paths in sorted(found.items()):
    print("=" * 100)
    print("FILE:", f)
    for p in paths:
        h = hashlib.md5(open(p, "rb").read()).hexdigest()[:12]
        print(f"   {h}  {os.path.getsize(p):>10}  {p}")

# For each unique vs_clockwork / create copy, inspect sequenced assembly recipes
def recipes_in(path, nested=False):
    out = []
    try:
        z = zipfile.ZipFile(path)
    except Exception as e:
        return [("ERR", str(e))]
    for n in z.namelist():
        low = n.lower()
        if low.endswith(".json") and ("/recipe/" in low or "/recipes/" in low):
            try:
                raw = z.read(n).decode("utf-8-sig")
            except Exception:
                continue
            if "create:sequenced_assembly" in raw:
                try:
                    d = json.loads(raw)
                except Exception as e:
                    out.append((n, "PARSEFAIL " + str(e)))
                    continue
                steps = []
                for s in (d.get("sequence") or []):
                    ings = s.get("ingredients")
                    steps.append((s.get("type"), ings if not isinstance(ings, list) else len(ings)))
                out.append((n, steps))
    return out

print("\n\n############ RECIPES PER COPY ############")
for f, paths in sorted(found.items()):
    for p in paths:
        r = recipes_in(p)
        if r:
            print("=" * 100)
            print(p)
            for n, s in r:
                print("    ", n, "->", s)
