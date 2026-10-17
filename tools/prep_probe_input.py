import json, os

SRC = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\src\main\resources\data\vs_clockwork\recipe"
OUT = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\tools\probe_in"
os.makedirs(OUT, exist_ok=True)
files = [
    os.path.join(SRC, "crafting", "incomplete_wanderwand.json"),
    os.path.join(SRC, "sequenced_assembly", "extendon_hose.json"),
    os.path.join(SRC, "sequenced_assembly", "universal_shaft_item.json"),
]
import zipfile
CREATE = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs\create-1.21.1-6.0.10.jar"
z = zipfile.ZipFile(CREATE)
for name, tag in [("data/create/recipe/sequenced_assembly/precision_mechanism.json", "create_precision_mechanism.json"),
                  ("data/create/recipe/sequenced_assembly/sturdy_sheet.json", "create_sturdy_sheet.json"),
                  ("data/create/recipe/sequenced_assembly/track.json", "create_track.json")]:
    with open(os.path.join(OUT, tag), "w", encoding="utf-8") as fh:
        fh.write(z.read(name).decode("utf-8"))
for f in files:
    dst = os.path.join(OUT, "ours_" + os.path.basename(f))
    with open(f, encoding="utf-8-sig") as fh:
        data = json.load(fh)
    with open(dst, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
    print(dst)
print("\n".join(sorted(os.path.join(OUT, x) for x in os.listdir(OUT))))
