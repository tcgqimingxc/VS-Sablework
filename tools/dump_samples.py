import json, os, zipfile, glob, io, sys

CREATE = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs\create-1.21.1-6.0.10.jar"

def show(jar, names):
    z = zipfile.ZipFile(jar)
    for n in names:
        try:
            data = z.read(n).decode("utf-8-sig")
        except KeyError:
            print(f"--- MISSING {n}")
            continue
        print(f"===== {os.path.basename(jar)} :: {n}")
        print(data)
        print()

print("############ CREATE OFFICIAL SAMPLES ############")
z = zipfile.ZipFile(CREATE)
seq = [n for n in z.namelist() if "/sequenced_assembly/" in n]
print("create sequenced_assembly files:", seq)
show(CREATE, ["data/create/recipe/sequenced_assembly/precision_mechanism.json",
              "data/create/recipe/sequenced_assembly/sturdy_sheet.json"])

fil = [n for n in z.namelist() if n.startswith("data/create/recipe/filling/")][:3]
print("filling samples:", fil)
show(CREATE, fil)
dep = [n for n in z.namelist() if n.startswith("data/create/recipe/deploying/")][:2]
show(CREATE, dep)
pres = [n for n in z.namelist() if n.startswith("data/create/recipe/pressing/")][:2]
show(CREATE, pres)
