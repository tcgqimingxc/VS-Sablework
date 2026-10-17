import json, os, re, sys, zipfile, io

ROOT = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\src\main\resources\data"
JAR = r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods\vs_clockwork-0.6.0-1.21.1.jar"
CREATE = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs\create-1.21.1-6.0.10.jar"
NEOFORM = r"C:\Users\yql11\.gradle\caches\neoformruntime\intermediate_results\compiledWithNeoForge_10533ef30658cc79fdd84080ba71d2227a64d92c_output.jar"

# ---------- known ids ----------
def lang_ids(jar):
    ids = set()
    try:
        z = zipfile.ZipFile(jar)
    except Exception:
        return ids
    for n in z.namelist():
        if n.endswith("lang/en_us.json"):
            try:
                d = json.loads(z.read(n).decode("utf-8-sig"))
            except Exception:
                continue
            ns = n.split("/")[1]
            for k in d:
                for pre in ("item.", "block.", "fluid.", "entity."):
                    if k.startswith(pre + ns + "."):
                        ids.add(ns + ":" + k[len(pre) + len(ns) + 1:])
                if k.startswith("itemGroup." + ns + "."):
                    ids.add(ns + ":" + k[len("itemGroup." + ns + "."):])
    return ids

def model_ids(jar):
    ids = set()
    try:
        z = zipfile.ZipFile(jar)
    except Exception:
        return ids
    for n in z.namelist():
        m = re.match(r"assets/([^/]+)/models/item/(.+)\.json$", n)
        if m:
            ids.add(m.group(1) + ":" + m.group(2))
    return ids

known = set()
known |= lang_ids(JAR)
known |= model_ids(JAR)
known |= lang_ids(CREATE)
known |= model_ids(CREATE)
known |= lang_ids(NEOFORM)
known |= model_ids(NEOFORM)
print(f"# known ids: ours={len(lang_ids(JAR) | model_ids(JAR))} create+vanilla+neoforge-> total {len(known)}")

problems = []
stats = {"files": 0}

ITEM_KEYS = {"item", "items", "id", "result", "ingredient", "output", "stack", "block", "tag", "name",
             "fluid", "entries", "values", "match", "drop", "alternatives", "children", "required"}

def walk(node, path, fname, filetype):
    if isinstance(node, dict):
        for k, v in node.items():
            if k in ("item", "items", "id", "result", "output", "ingredient", "block", "stack"):
                check_ids(v, f"{fname}:{path}/{k}", filetype)
            walk(v, f"{path}/{k}", fname, filetype)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            walk(v, f"{path}[{i}]", fname, filetype)

def check_ids(v, where, filetype):
    vals = v if isinstance(v, list) else [v]
    for x in vals:
        if isinstance(x, str) and re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", x):
            if x not in known:
                problems.append((where, x, filetype))

for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if not f.endswith(".json"):
            continue
        p = os.path.join(dp, f)
        stats["files"] += 1
        rel = os.path.relpath(p, ROOT).replace("\\", "/")
        try:
            raw = open(p, encoding="utf-8-sig").read()
            d = json.loads(raw)
        except Exception as e:
            problems.append((rel, "JSON PARSE FAIL: " + str(e), "parse"))
            continue
        if raw.strip() == "":
            problems.append((rel, "EMPTY FILE", "parse"))
        walk(d, "", rel, d.get("type", "?") if isinstance(d, dict) else "?")

print(f"# files checked: {stats['files']}")
print(f"# unresolved-id findings: {len(problems)}")
seen = set()
for w, x, t in problems:
    key = (w.split(":")[0], x)
    if key in seen:
        continue
    seen.add(key)
    print(f"   {w}  ->  {x}   [{t}]")
