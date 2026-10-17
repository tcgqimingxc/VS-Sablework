import zipfile, json, os, re

ROOT = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\src\main\resources\data"
VS = r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods\vs_clockwork-0.6.0-1.21.1.jar"
CREATE = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs\create-1.21.1-6.0.10.jar"
VANILLA = r"C:\Users\yql11\.gradle\caches\neoformruntime\intermediate_results\stripClient_5366ba7cadb79a79e1f1777bdebc8f7fc800c417_resourcesOutput.jar"
VANILLA_CLASSES = r"C:\Users\yql11\.gradle\caches\neoformruntime\intermediate_results\compiledWithNeoForge_10533ef30658cc79fdd84080ba71d2227a64d92c_output.jar"

def class_strings(jar, only_prefix=None):
    """all UTF-ish strings appearing in .class constant pools (crude but effective for registrate ids)"""
    out = set()
    z = zipfile.ZipFile(jar)
    for n in z.namelist():
        if not n.endswith(".class"):
            continue
        if only_prefix and not n.startswith(only_prefix):
            continue
        data = z.read(n)
        for tok in re.split(rb"[^\x20-\x7e]+", data):
            if len(tok) < 3:
                continue
            s = tok.decode("latin1")
            out.add(s)
            if "/" in s:
                out.add(s.split("/")[-1])
    return out

def asset_ids(jar):
    out = set()
    z = zipfile.ZipFile(jar)
    for n in z.namelist():
        m = re.match(r"assets/([^/]+)/models/(?:item|block)/(.+)\.json$", n)
        if m:
            out.add(m.group(1) + ":" + m.group(2))
    return out

print("# building registration sets ...")
vs_strings = class_strings(VS)
create_strings = class_strings(CREATE)
vanilla_strings = class_strings(VANILLA_CLASSES, only_prefix="net/minecraft/")
vs_assets = asset_ids(VS)

def registered(iid):
    ns, _, path = iid.partition(":")
    leaf = path.split("/")[-1]
    if ns == "vs_clockwork":
        # registered only if the id (or its leaf) appears in the mod's class files
        return (path in vs_strings) or (leaf in vs_strings)
    if ns == "create":
        return (path in create_strings) or (leaf in create_strings)
    if ns in ("minecraft", "neoforge", "c"):
        return True
    return None  # unknown namespace / other mod

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

bad_json = []
for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if not f.endswith(".json"):
            continue
        p = os.path.join(dp, f)
        rel = os.path.relpath(p, ROOT).replace("\\", "/")
        try:
            d = json.loads(open(p, encoding="utf-8-sig").read())
        except Exception as e:
            bad_json.append((rel, str(e)))
            continue
        walk(d, rel)

print(f"# files: {sum(1 for dp,dn,fn in os.walk(ROOT) for f in fn if f.endswith('.json'))}, unparsable: {len(bad_json)}")
for rel, e in bad_json:
    print("   PARSE FAIL", rel, e)

unres = {k: v for k, v in refs.items() if registered(k) is False}
print(f"# distinct referenced ids: {len(refs)};  UNREGISTERED: {len(unres)}")
for k in sorted(unres):
    where = sorted(unres[k])
    print(f"   *** {k}  referenced by {len(where)} file(s), e.g. {where[:4]}")

other = {k: v for k, v in refs.items() if registered(k) is None}
print(f"# references into other namespaces (not installed here): {len(other)}")
for k in sorted(other):
    print(f"   (external) {k}   e.g. {sorted(other[k])[:2]}")
