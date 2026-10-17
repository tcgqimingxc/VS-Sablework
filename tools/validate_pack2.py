import json, os, re, zipfile

ROOT = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\src\main\resources\data"
JAR = r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods\vs_clockwork-0.6.0-1.21.1.jar"
CREATE = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs\create-1.21.1-6.0.10.jar"
NFR = r"C:\Users\yql11\.gradle\caches\neoformruntime\intermediate_results"
VANILLA_RES = os.path.join(NFR, "stripClient_5366ba7cadb79a79e1f1777bdebc8f7fc800c417_resourcesOutput.jar")

def ids_from(jar):
    out = set()
    try:
        z = zipfile.ZipFile(jar)
    except Exception:
        return out
    for n in z.namelist():
        m = re.match(r"assets/([^/]+)/lang/en_us\.json$", n)
        if m:
            ns = m.group(1)
            try:
                d = json.loads(z.read(n).decode("utf-8-sig"))
            except Exception:
                continue
            for k in d:
                for pre in ("item.", "block.", "fluid."):
                    if k.startswith(pre + ns + "."):
                        out.add(ns + ":" + k[len(pre):])
        m = re.match(r"assets/([^/]+)/models/(item|block)/(.+)\.json$", n)
        if m:
            out.add(m.group(1) + ":" + m.group(3))
        m = re.match(r"data/([^/]+)/tags/(item|block|fluid)/(.+)\.json$", n)
        if m:
            out.add("#" + m.group(1) + ":" + m.group(3))
    return out

known = set()
for j in (JAR, CREATE, VANILLA_RES):
    known |= ids_from(j)
ours = ids_from(JAR)
print(f"# known item/block ids: {len(known)} (ours {len(ours)})")

problems = []
files = 0
INTERESTING = {"item", "items", "id", "result", "ingredient", "output", "block", "stack", "target", "drop"}

def walk(node, path, rel, ftype):
    if isinstance(node, dict):
        for k, v in node.items():
            if k in INTERESTING:
                for x in (v if isinstance(v, list) else [v]):
                    if isinstance(x, str) and re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", x):
                        if x not in known:
                            problems.append((rel, f"{path}/{k}", x, ftype))
            walk(v, f"{path}/{k}", rel, ftype)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            walk(v, f"{path}[{i}]", rel, ftype)

for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if not f.endswith(".json"):
            continue
        p = os.path.join(dp, f)
        files += 1
        rel = os.path.relpath(p, ROOT).replace("\\", "/")
        raw = open(p, encoding="utf-8-sig").read()
        try:
            d = json.loads(raw)
        except Exception as e:
            problems.append((rel, "<json>", "PARSE FAIL: " + str(e), "parse"))
            continue
        walk(d, "", rel, d.get("type", "?") if isinstance(d, dict) else "?")

print(f"# files checked: {files}")
print(f"# findings: {len(problems)}")
for a, b, c, d in problems:
    print(f"   {a}  {b} -> {c}   [{d}]")
