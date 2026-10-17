import zipfile, io, os, glob, json, re, hashlib

MODS = r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods"
print("### raw regex scan for empty ingredient arrays / suspicious ingredient shapes")
pat_empty = re.compile(rb'"ingredients"\s*:\s*\[\s*\]')
pat_fluid = re.compile(rb'"ingredients"\s*:\s*\[\s*\{[^}]*"(type|fluid|fluid_tag)"')

def scan(z, label, depth=0):
    for n in z.namelist():
        low = n.lower()
        if low.endswith(".json") and ("/recipe/" in low or "/recipes/" in low):
            try:
                raw = z.read(n)
            except Exception:
                continue
            if b"sequenced_assembly" in raw:
                if pat_empty.search(raw):
                    print("  !!!! EMPTY ingredients:", label, n)
                if pat_fluid.search(raw):
                    print("  ~~~ fluid-looking ingredients:", label, n)
        elif low.endswith(".json"):
            try:
                raw = z.read(n)
            except Exception:
                continue
            if pat_empty.search(raw) and b"sequenced_assembly" in raw:
                print("  !!!! EMPTY ingredients (other path):", label, n)
    for n in z.namelist():
        if n.lower().endswith(".jar") and depth < 3:
            try:
                scan(zipfile.ZipFile(io.BytesIO(z.read(n))), label + "!" + os.path.basename(n), depth + 1)
            except Exception:
                pass

for j in sorted(glob.glob(os.path.join(MODS, "*.jar"))):
    h = hashlib.md5(open(j, "rb").read()).hexdigest()[:12]
    if os.path.basename(j) != "vs_clockwork-0.6.0-1.21.1.jar":
        continue
    print("=" * 100)
    print("JAR", os.path.basename(j), h, os.path.getsize(j))
    scan(zipfile.ZipFile(j), os.path.basename(j))
    z = zipfile.ZipFile(j)
    for n in z.namelist():
        if "/recipe" in n and n.endswith(".json"):
            raw = z.read(n)
            if b"sequenced_assembly" in raw:
                print("-" * 80)
                print("FILE:", n)
                print(raw.decode("utf-8-sig"))
