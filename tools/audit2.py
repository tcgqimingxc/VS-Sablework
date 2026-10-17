import zipfile, json, os, re

ROOT = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\src\main\resources\data"
VS = r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods\vs_clockwork-0.6.0-1.21.1.jar"
CREATE = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs\create-1.21.1-6.0.10.jar"

def class_blob(jar):
    z = zipfile.ZipFile(jar)
    return [(n, z.read(n)) for n in z.namelist() if n.endswith(".class")]

print("# loading class files ...")
vs_cls = class_blob(VS)
cr_cls = class_blob(CREATE)

def refs_in(classes, needle):
    b = needle.encode()
    return [n.split("/")[-1] for n, d in classes if b in d]

refs = {}
def walk(node, rel):
    if isinstance(node, dict):
        for k, v in node.items():
            if k in ("item", "id"):
                for x in (v if isinstance(v, list) else [v]):
                    if isinstance(x, str) and re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", x):
                        refs.setdefault(x, set()).add(rel)
            walk(v, rel)
    elif isinstance(node, list):
        for v in node:
            walk(v, rel)

files = 0
for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if f.endswith(".json"):
            files += 1
            p = os.path.join(dp, f)
            rel = os.path.relpath(p, ROOT).replace("\\", "/")
            try:
                d = json.loads(open(p, encoding="utf-8-sig").read())
            except Exception as e:
                print("   PARSE FAIL", rel, e)
                continue
            walk(d, rel)

print(f"# json files: {files}; distinct referenced ids: {len(refs)}")
print("\n### vs_clockwork: ids with NO class reference (== not registered) ###")
bad = []
for k in sorted(refs):
    ns = k.split(":")[0]
    if ns != "vs_clockwork":
        continue
    hits = refs_in(vs_cls, k.split(":")[1])
    if not hits:
        bad.append((k, sorted(refs[k])))
for k, where in bad:
    print(f"   *** {k}   -> {where}")
if not bad:
    print("   (none)")

print("\n### create: ids with NO class reference ###")
bad2 = []
for k in sorted(refs):
    ns = k.split(":")[0]
    if ns != "create":
        continue
    hits = refs_in(cr_cls, k.split(":")[1])
    if not hits:
        bad2.append((k, sorted(refs[k])))
for k, where in bad2:
    print(f"   *** {k}   -> {where}")
if not bad2:
    print("   (none)")

# ingredient-position check for sequenced assembly
print("\n### every create:sequenced_assembly step ingredient, resolved ###")
for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if not f.endswith(".json"):
            continue
        p = os.path.join(dp, f)
        raw = open(p, encoding="utf-8-sig").read()
        if "create:sequenced_assembly" not in raw:
            continue
        d = json.loads(raw)
        rel = os.path.relpath(p, ROOT).replace("\\", "/")
        print(f"   {rel}")
        for i, step in enumerate(d.get("sequence", [])):
            ids = []
            def collect(node):
                if isinstance(node, dict):
                    if "item" in node and isinstance(node["item"], str):
                        ids.append(node["item"])
                    for v in node.values():
                        collect(v)
                elif isinstance(node, list):
                    for v in node:
                        collect(v)
            collect(step.get("ingredients"))
            res = []
            for x in ids:
                ns = x.split(":")[0]
                if ns == "vs_clockwork":
                    ok = bool(refs_in(vs_cls, x.split(":")[1]))
                elif ns == "create":
                    ok = bool(refs_in(cr_cls, x.split(":")[1]))
                else:
                    ok = True
                res.append(f"{x}{'' if ok else '  <<< UNRESOLVABLE -> CRASH'}")
            print(f"      step{i} {step.get('type'):26s} ingredients={len(ids)} : {res}")
