import json, os, zipfile, glob, sys, io

roots = [
    r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods",
    r"C:\Users\yql11\AppData\Roaming\PrismLauncher\instances\CSN_The Song Of The Sky\minecraft\mods",
]
jars = []
for r in roots:
    if os.path.isdir(r):
        jars += glob.glob(os.path.join(r, "*.jar"))
jars = sorted(set(jars))
print("jars:", len(jars), file=sys.stderr)

TARGET = "create:sequenced_assembly"
report = []
for jar in jars:
    try:
        z = zipfile.ZipFile(jar)
    except Exception as e:
        continue
    for n in z.namelist():
        low = n.lower()
        if not low.endswith(".json"):
            continue
        if "/recipe/" not in low and "/recipes/" not in low:
            continue
        try:
            raw = z.read(n).decode("utf-8-sig")
        except Exception:
            continue
        if TARGET not in raw:
            continue
        try:
            d = json.loads(raw)
        except Exception as e:
            report.append((os.path.basename(jar), n, "JSON-PARSE-FAIL", str(e), None))
            continue
        problems = []
        seq = d.get("sequence")
        if not isinstance(seq, list) or not seq:
            problems.append("sequence missing/empty")
            seq = seq if isinstance(seq, list) else []
        for i, step in enumerate(seq):
            if not isinstance(step, dict):
                problems.append(f"step{i}: not an object")
                continue
            st = step.get("type", "<no type>")
            ings = step.get("ingredients")
            if ings is None:
                problems.append(f"step{i}({st}): NO ingredients field")
            elif not isinstance(ings, list):
                problems.append(f"step{i}({st}): ingredients not a list")
            elif len(ings) == 0:
                problems.append(f"step{i}({st}): EMPTY ingredients  <== CRASH CANDIDATE")
            res = step.get("results")
            if res is None:
                problems.append(f"step{i}({st}): no results")
            elif isinstance(res, list) and len(res) == 0:
                problems.append(f"step{i}({st}): empty results")
        if "ingredient" not in d:
            problems.append("top-level ingredient missing")
        if "transitional_item" not in d:
            problems.append("transitional_item missing")
        if "results" not in d:
            problems.append("top-level results missing")
        report.append((os.path.basename(jar), n, d.get("type"), "; ".join(problems) or "ok",
                       json.dumps(seq and [ [s.get("type"), len(s.get("ingredients") or []) if isinstance(s.get("ingredients"), list) else s.get("ingredients")] for s in seq if isinstance(s, dict)])))

print("=== sequenced_assembly recipes found:", len(report))
for jar, n, t, prob, steps in report:
    flag = "  " if prob == "ok" else "!!"
    print(f"{flag} {jar} :: {n}\n     problems: {prob}\n     steps: {steps}")
