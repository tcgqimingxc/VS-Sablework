import zipfile, json, re, sys

CREATE = r"C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs\create-1.21.1-6.0.10.jar"
VANILLA = r"C:\Users\yql11\.gradle\caches\neoformruntime\intermediate_results\stripClient_5366ba7cadb79a79e1f1777bdebc8f7fc800c417_resourcesOutput.jar"

def item_ids(jar):
    """items/blocks a pack registers, approximated by lang keys + models + blockstates"""
    out = set()
    z = zipfile.ZipFile(jar)
    for n in z.namelist():
        m = re.match(r"assets/([^/]+)/lang/en_us\.json$", n)
        if m:
            ns = m.group(1)
            try:
                d = json.loads(z.read(n).decode("utf-8-sig"))
            except Exception:
                continue
            for k in d:
                for pre in ("item.", "block."):
                    if k.startswith(pre + ns + "."):
                        out.add(ns + ":" + k[len(pre) + len(ns) + 1:])
        m = re.match(r"assets/([^/]+)/models/(item|block)/(.+)\.json$", n)
        if m:
            out.add(m.group(1) + ":" + m.group(3))
    return out

external = item_ids(CREATE) | item_ids(VANILLA)

copies = [
    (r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods\vs_clockwork-0.6.0-1.21.1.jar", "INSTALLED (current build)"),
    (r"C:\Users\yql11\AppData\Roaming\PrismLauncher\instances\CSN_The Song Of The Sky\minecraft\mods\vs_clockwork-0.6.0-1.21.1.jar", "PRISM copy"),
    (r"C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\CSN_The Song Of The Sky\mods\vs_clockwork-0.6.0-1.21.1.jar", "CSN copy (has the 4 loot tables = crash-time profile)"),
]

def walk_ids(node, out):
    if isinstance(node, dict):
        for k, v in node.items():
            if k in ("item", "id"):
                for x in (v if isinstance(v, list) else [v]):
                    if isinstance(x, str) and ":" in x:
                        out.add(x)
            walk_ids(v, out)
    elif isinstance(node, list):
        for v in node:
            walk_ids(v, out)

for path, tag in copies:
    z = zipfile.ZipFile(path)
    own = item_ids(path)
    all_known = own | external
    print("=" * 95)
    print(f"{tag}\n   {path}")
    print(f"   items this build registers (lang/models): {len(own)}")
    for n in sorted(z.namelist()):
        if not n.endswith(".json"):
            continue
        try:
            raw = z.read(n).decode("utf-8-sig")
        except Exception:
            continue
        if "create:sequenced_assembly" not in raw:
            continue
        d = json.loads(raw)
        ids = set()
        walk_ids(d.get("sequence", []), ids)
        walk_ids(d.get("results", []), ids)
        walk_ids(d.get("transitional_item", d.get("transitionalItem", {})), ids)
        ing = d.get("ingredient")
        walk_ids(ing if ing else {}, ids)
        missing = sorted(i for i in ids if i not in all_known and not i.startswith("#"))
        print(f"   RECIPE {n}")
        print(f"      referenced items: {len(ids)}   UNRESOLVABLE (= crash): {missing if missing else 'none'}")
