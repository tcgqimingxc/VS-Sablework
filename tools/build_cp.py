import json, os, glob, sys

MC = r"C:\Users\yql11\OneDrive\Desktop\.minecraft"
ver_json = os.path.join(MC, r"versions\1.21.1-NeoForge_21.1.251\1.21.1-NeoForge_21.1.251.json")
data = json.load(open(ver_json, encoding="utf-8"))
cp = []
missing = []
for lib in data.get("libraries", []):
    name = lib.get("name", "")
    if not name:
        continue
    if "natives" in lib and not lib.get("downloads", {}).get("artifact"):
        continue
    parts = name.split(":")
    if len(parts) < 3:
        continue
    group, artifact, version = parts[0], parts[1], parts[2]
    path = os.path.join(MC, "libraries", *group.split("."), artifact, version, f"{artifact}-{version}.jar")
    if os.path.exists(path):
        cp.append(path)
    else:
        # try classifier-less variants
        missing.append(path)

print("# from version json:", len(cp), "missing:", len(missing), file=sys.stderr)
for m in missing[:20]:
    print("#   MISSING", m, file=sys.stderr)

# the patched (srg) minecraft client jar used at runtime
srg = glob.glob(os.path.join(MC, "libraries", "net", "minecraft", "client", "*", "*.jar"))
print("# srg candidates:", srg, file=sys.stderr)
cp += srg

extra_dirs = [
    r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs",
]
for d in extra_dirs:
    for j in glob.glob(os.path.join(d, "*.jar")):
        cp.append(j)
# create only (avoid duplicate flywheel/ponder if not needed) -- include all libs anyway

out = ";".join(cp)
open(r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\tools\cp.txt", "w", encoding="utf-8").write(out)
print("# classpath entries:", len(cp), file=sys.stderr)
print(out)
