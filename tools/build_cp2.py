import json, os, glob, sys

MC = r"C:\Users\yql11\OneDrive\Desktop\.minecraft"
NEOFORM = r"C:\Users\yql11\.gradle\caches\neoformruntime\intermediate_results\compiledWithNeoForge_10533ef30658cc79fdd84080ba71d2227a64d92c_output.jar"
ver_json = os.path.join(MC, r"versions\1.21.1-NeoForge_21.1.251\1.21.1-NeoForge_21.1.251.json")
data = json.load(open(ver_json, encoding="utf-8"))
cp = [NEOFORM]
skip_markers = ("net.minecraft:client", "net.neoforged:neoforge", "org.lwjgl", "com.mojang:text2speech")
for lib in data.get("libraries", []):
    name = lib.get("name", "")
    if not name or any(name.startswith(s) for s in skip_markers):
        continue
    parts = name.split(":")
    if len(parts) < 3:
        continue
    group, artifact, version = parts[0], parts[1], parts[2]
    path = os.path.join(MC, "libraries", *group.split("."), artifact, version, f"{artifact}-{version}.jar")
    if os.path.exists(path) and path not in cp:
        cp.append(path)

libdir = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs"
for j in glob.glob(os.path.join(libdir, "*.jar")):
    if "cc-tweaked" in j or "jei" in j:
        continue  # not needed, may pull extra deps
    cp.append(j)

out = ";".join(cp)
open(r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\tools\cp2.txt", "w", encoding="utf-8").write(out)
print("# entries:", len(cp), file=sys.stderr)
for c in cp:
    print("   ", os.path.basename(c), file=sys.stderr)
