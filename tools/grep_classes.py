import zipfile, io, os, glob, sys

MODS = r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods"
NEEDLES = [b"sequenced_assembly", b"SequencedAssemblyRecipe", b"SequencedRecipe", b"DynamicPack",
           b"addPackFinders", b"RegisterRecipeSerializers", b"RecipeManager"]
SKIP = ("create-1.21.1-6.0.10.jar",)

def scan(z, label, depth=0):
    for n in z.namelist():
        if n.endswith(".class"):
            try:
                d = z.read(n)
            except Exception:
                continue
            for nd in NEEDLES:
                if nd in d:
                    print(f"  [{nd.decode()}] {label} :: {n}")
                    break
    for n in z.namelist():
        if n.lower().endswith(".jar") and depth < 3:
            try:
                nz = zipfile.ZipFile(io.BytesIO(z.read(n)))
            except Exception:
                continue
            print(f"  >> nested {label} :: {n}")
            scan(nz, label + "!" + os.path.basename(n), depth + 1)

for j in sorted(glob.glob(os.path.join(MODS, "*.jar"))):
    b = os.path.basename(j)
    if b in SKIP:
        continue
    print("=" * 90)
    print("JAR", b)
    scan(zipfile.ZipFile(j), b)
