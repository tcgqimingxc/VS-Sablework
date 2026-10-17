import zipfile, io, os, glob, re

JAR = r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods\vs_clockwork-0.6.0-1.21.1.jar"
ids = ["incomplete_wanderwand", "incomplete_hose_spool", "incomplete_telescoping_mechanism",
       "universal_shaft_item", "extendon_hose", "gas_nozzle", "hose_port", "delivery_chute",
       "delivery_cannon", "duct"]
z = zipfile.ZipFile(JAR)
print("### occurrences of ids inside .class files (registry code):")
for i in ids:
    hits = []
    for n in z.namelist():
        if not n.endswith(".class"):
            continue
        if i.encode() in z.read(n):
            hits.append(n)
    print(f"  {i:35s} -> {len(hits)} class refs: {hits[:6]}")

print("\n### all data/vs_clockwork recipe ids present (json names):")
for n in sorted(z.namelist()):
    if "/recipe" in n and n.endswith(".json"):
        print("   ", n)

print("\n### assets item models present for the incomplete ids:")
for i in ids:
    m = f"assets/vs_clockwork/models/item/{i}.json"
    print(f"  {i:35s} model={m in z.namelist()}")
