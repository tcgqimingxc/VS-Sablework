#!/usr/bin/env python3
"""生成「方块属性快照」：每个注册方块的全部 BlockState 属性与合法取值。

为什么需要这个快照（而不是在守门脚本里靠正则猜）：
  `check_assets.py [12]`（blockstate 属性覆盖）必须知道「代码里这个方块到底有哪些属性、
  每个属性有哪些合法取值」，才能枚举出全部方块状态、和 `blockstates/<id>.json` 里的
  variants/multipart 条件做匹配。属性来自：
    * 方块自己的 `createBlockStateDefinition`（源码里能看到），
    * **父类**（Create 的 `DirectionalKineticBlock`/`KineticBlock`…、原版 `SlabBlock`/`StairBlock`…），
    * Create 的自定义属性（`axis_along_first`、duct 的四态枚举…）。
  后两类只看本仓库源码是**看不到**的（Create 只有 jar），所以这里直接实例化方块类 ——
  1.21.1 的 `StateDefinition` 就是 `BlockBehaviour` 构造器里由 `createBlockStateDefinition`
  建好的，实例化即得真值（`Property#getName` 给出的正是 blockstate JSON 里写的字面量，
  对 EnumProperty 来说是 `StringRepresentable` 的序列化名，**不是**枚举常量名 —— 这点搞错会
  把 `"lit": "true"` 误判成非法值，第一版分析脚本就踩过）。

用法（只在方块属性/父类改动后需要重跑）：
    python tools/probe_blockstates.py            # 探测并写 tools/blockstate_properties.json
    python tools/probe_blockstates.py --check    # 只比对快照是否与当前编译产物一致

依赖：本机 JDK（JAVA_HOME 或 PATH）+ 已构建的 `build/classes/java/main` + NeoForge 依赖。
探测失败时**不会**写坏已有快照（先写临时文件再原子替换）。
"""
import argparse
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
SNAPSHOT = os.path.join(TOOLS, "blockstate_properties.json")
MC_ROOT = r"C:\Users\yql11\OneDrive\Desktop\.minecraft"
JAVA_FALLBACK = r"C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"

JAVA_SRC = r"""
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

/** 一次性开发用探针：实例化方块类，dump 属性与合法取值（见 tools/probe_blockstates.py）。 */
public class BlockStateProbe {

    static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static String propName(Property p, Object v) {
        return p.getName((Comparable) v);
    }

    static void unfreeze(Object registry) throws Exception {
        for (Class<?> c = registry.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("frozen");
                f.setAccessible(true);
                f.setBoolean(registry, false);
                return;
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // FML 没启动时 FeatureFlags 的静态初始化会去问 LoadingModList，先塞个空壳
        try {
            net.neoforged.fml.loading.LoadingModList.of(
                    List.of(), List.of(), List.of(), List.of(), Map.of());
        } catch (Throwable ignored) {
            // 老版本 FML 没有这个工厂方法也无所谓
        }
        String bootstrapError = "";
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable t) {
            // CreativeModeTabs.validate() -> Language.loadDefault() 需要原版资源包，
            // 拿不到资源时在这一步失败；注册表此时已经冻结/建好，后面照样能读属性。
            Throwable c = t;
            while (c.getCause() != null && c.getCause() != c) c = c.getCause();
            bootstrapError = c.getClass().getName() + ": " + c.getMessage();
        }
        // Block 构造器会给 BuiltInRegistries.BLOCK 建 intrusive holder，而注册表已冻结
        try {
            unfreeze(net.minecraft.core.registries.BuiltInRegistries.BLOCK);
        } catch (Throwable ignored) {
        }

        List<String> lines = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder("{\n");
        int ok = 0, fail = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] cols = line.split("\t");
            String id = cols[0];
            String cn = cols.length > 1 ? cols[1].trim() : "";
            String extra = cols.length > 2 ? cols[2].trim() : "";
            String json;
            try {
                json = probe(cn, extra);
                ok++;
            } catch (Throwable t) {
                Throwable c = t;
                while (c.getCause() != null && c.getCause() != c) c = c.getCause();
                json = "{\"error\":\"" + esc(c.getClass().getName() + ": " + c.getMessage()) + "\"}";
                fail++;
                System.err.println("FAIL " + id + " (" + cn + "): " + c);
            }
            out.append("  \"").append(esc(id)).append("\": ").append(json).append(",\n");
        }
        out.append("  \"__done\": {\"ok\": ").append(ok).append(", \"fail\": ").append(fail)
           .append(", \"bootstrapError\": \"").append(esc(bootstrapError)).append("\"}\n}\n");
        Files.write(Paths.get(args[1]), out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("probe done: ok=" + ok + " fail=" + fail);
    }

    static String probe(String cn, String extra) throws Exception {
        Class<?> c = Class.forName(cn, true, BlockStateProbe.class.getClassLoader());
        Constructor<?> ctor = null;
        Object[] argv = null;
        try {
            ctor = c.getDeclaredConstructor(BlockBehaviour.Properties.class);
            argv = new Object[]{BlockBehaviour.Properties.of()};
        } catch (NoSuchMethodException e) {
            // 额外参数按「非 Properties 参数」的顺序给：枚举常量名，或 <state>（StairBlock 那种）
            String[] names = extra.isEmpty() ? new String[0] : extra.split(",");
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                Class<?>[] pt = k.getParameterTypes();
                if (pt.length != names.length + 1) continue;
                Object[] a = new Object[pt.length];
                int next = 0;
                boolean good = true;
                for (int i = 0; i < pt.length; i++) {
                    if (pt[i].equals(BlockBehaviour.Properties.class)) {
                        a[i] = BlockBehaviour.Properties.of();
                        continue;
                    }
                    if (next >= names.length) {
                        good = false;
                        break;
                    }
                    String want = names[next++].trim();
                    if (pt[i].isEnum()) {
                        try {
                            @SuppressWarnings({"unchecked", "rawtypes"})
                            Object v = Enum.valueOf((Class<? extends Enum>) pt[i], want);
                            a[i] = v;
                        } catch (IllegalArgumentException bad) {
                            good = false;
                            break;
                        }
                    } else if (pt[i].equals(net.minecraft.world.level.block.state.BlockState.class)) {
                        a[i] = new Block(BlockBehaviour.Properties.of()).defaultBlockState();
                    } else {
                        good = false;
                        break;
                    }
                }
                if (good && next == names.length) {
                    ctor = k;
                    argv = a;
                    break;
                }
            }
        }
        if (ctor == null) {
            StringBuilder sb = new StringBuilder("{\"error\":\"没有可用的构造器: ");
            for (Constructor<?> k : c.getDeclaredConstructors()) sb.append(esc(k.toString())).append("; ");
            return sb.append("\"}").toString();
        }
        ctor.setAccessible(true);
        Object block = ctor.newInstance(argv);
        StateDefinition<?, ?> def = ((Block) block).getStateDefinition();

        StringBuilder sb = new StringBuilder();
        sb.append("{\"class\":\"").append(esc(cn)).append("\",\"properties\":[");
        boolean first = true;
        for (Property<?> p : def.getProperties()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":\"").append(esc(p.getName())).append("\",\"type\":\"")
              .append(esc(p.getClass().getSimpleName())).append("\",\"values\":[");
            boolean f2 = true;
            for (Object v : p.getPossibleValues()) {
                if (!f2) sb.append(",");
                f2 = false;
                sb.append("\"").append(esc(propName(p, v))).append("\"");
            }
            sb.append("]}");
        }
        sb.append("],\"stateCount\":").append(def.getPossibleStates().size()).append("}");
        return sb.toString();
    }
}
"""


# ---------------------------------------------------------------- 路径发现

def find_java():
    home = os.environ.get("JAVA_HOME")
    cands = []
    if home:
        cands.append(home)
    cands.append(JAVA_FALLBACK)
    for h in cands:
        javac = os.path.join(h, "bin", "javac.exe")
        java = os.path.join(h, "bin", "java.exe")
        if os.path.isfile(javac) and os.path.isfile(java):
            return javac, java
    javac = shutil.which("javac")
    java = shutil.which("java")
    if javac and java:
        return javac, java
    return None, None


def find_version_libs():
    """按实例的版本 JSON 拼出运行时库（版本必须与游戏一致，否则 fastutil 之类会 NoSuchMethodError）。"""
    best, parts = None, []
    vers = os.path.join(MC_ROOT, "versions")
    if not os.path.isdir(vers):
        return parts
    for name in sorted(os.listdir(vers)):
        j = os.path.join(vers, name, name + ".json")
        if not os.path.isfile(j):
            continue
        try:
            data = json.load(open(j, encoding="utf-8"))
        except Exception:  # noqa: BLE001
            continue
        names = " ".join(lib.get("name", "") for lib in data.get("libraries", []))
        if "neoforge" not in names and "fancymodloader" not in names:
            continue
        libs, ok = [], 0
        for lib in data.get("libraries", []):
            path = (lib.get("downloads", {}).get("artifact") or {}).get("path")
            if path:
                p = os.path.join(MC_ROOT, "libraries", path.replace("/", os.sep))
            else:
                g, a, v = (lib["name"].split(":") + ["", ""])[:3]
                p = os.path.join(MC_ROOT, "libraries", g.replace(".", os.sep), a, v, f"{a}-{v}.jar")
            if os.path.isfile(p):
                libs.append(p)
                ok += 1
        # 选 NeoForge 版本号最大的那个（目录里往往堆着好几个实例的历史版本；
        # 库坐标形如 net.neoforged:neoforge:21.1.251）
        m = re.search(r"neoforge:(\d+\.\d+\.\d+)", names)
        key = m.group(1) if m else name
        if best is None or key > best:
            best, parts = key, libs
    return parts


def build_classpath(project):
    parts = []
    classes = os.path.join(project, "build", "classes", "java", "main")
    if not os.path.isdir(classes):
        return None, f"找不到编译产物 {classes}（先跑一次 gradlew build）"
    parts.append(classes)
    resources = os.path.join(project, "build", "resources", "main")
    if os.path.isdir(resources):
        parts.append(resources)
    res = glob.glob(os.path.join(project, "build", "moddev", "artifacts",
                                 "*client-extra*minecraft-resources.jar"))
    parts += res                       # 原版资源（Language.loadDefault 要读 en_us.json）
    mc = glob.glob(os.path.join(os.path.expanduser("~"), ".gradle", "caches", "neoformruntime",
                                "intermediate_results", "compiledWithNeoForge_*_output.jar"))
    mc += glob.glob(os.path.join(project, "build", "moddev", "artifacts", "neoforge-*-merged.jar"))
    if not mc:
        return None, "找不到 MC+NeoForge 的已改名类（neoformruntime 产物或 build/moddev/artifacts）"
    parts += sorted(mc)
    libs = os.path.join(project, "libs")
    if os.path.isdir(libs):
        parts += [os.path.join(libs, f) for f in sorted(os.listdir(libs)) if f.endswith(".jar")]
    parts += find_version_libs()
    seen, out = set(), []
    for p in parts:
        if p not in seen and os.path.exists(p):
            seen.add(p)
            out.append(p)
    return out, None


# ---------------------------------------------------------------- 类名解析

def _split_args(text):
    """按顶层逗号切分构造参数（`new StairBlock(A.getDefaultState(), p)` 这种）。"""
    out, depth, cur = [], 0, ""
    for ch in text:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    return [a.strip() for a in out]


def _extra_args(rest, lam):
    """`p -> new XBlock(A.getDefaultState(), p)` -> 非 Properties 参数的占位（枚举常量名 / <state>）。"""
    m = re.search(r"new\s+([A-Z]\w*)\s*\(", rest)
    if not m:
        return None, []
    name = m.group(1)
    i, depth, cur, args = m.end(), 1, "", []
    while i < len(rest) and depth > 0:
        ch = rest[i]
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth -= 1
            if depth == 0:
                break
        if ch == "," and depth == 1:
            args.append(cur.strip())
            cur = ""
        else:
            cur += ch
        i += 1
    if cur.strip():
        args.append(cur.strip())
    extras = []
    for a in args:
        if a == lam:
            continue
        if "(" not in a and re.fullmatch(r"[A-Za-z_][\w.]*\.[A-Z_][A-Z0-9_]*", a):
            extras.append(a.split(".")[-1])
        else:
            extras.append("<state>")
    return name, extras


def block_class_map(project):
    """从 Java 源码里抽出 方块 id -> (类全名, 额外构造参数)。

    只处理 Registrate 的 `.block("id", X::new)` / `.block("id", p -> new X(p, KIND))`
    以及本仓库 CWBlocks 的 `simple(...)` / `ore(...)` 辅助方法。
    """
    src = os.path.join(project, "src", "main", "java")
    files = {}
    for dp, _, fs in os.walk(src):
        for f in fs:
            if f.endswith(".java"):
                p = os.path.join(dp, f)
                files[p] = open(p, encoding="utf-8", errors="replace").read()

    cls2fqn = {}
    for p, t in files.items():
        pkg = re.search(r"^package\s+([\w.]+);", t, re.M)
        pkg = pkg.group(1) if pkg else ""
        for m in re.finditer(r"\b(?:public\s+|final\s+|abstract\s+)*(?:class|record|enum)\s+(\w+)", t):
            cls2fqn.setdefault(m.group(1), f"{pkg}.{m.group(1)}")

    vanilla = {
        "Block": "net.minecraft.world.level.block.Block",
        "SlabBlock": "net.minecraft.world.level.block.SlabBlock",
        "StairBlock": "net.minecraft.world.level.block.StairBlock",
    }
    entries, unresolved = {}, []
    for p, t in files.items():
        if not (p.endswith("Index.java") or p.endswith("CWBlocks.java")):
            continue
        for m in re.finditer(r'\.block\(\s*"([a-z0-9_]+)"\s*,([^\n]*)', t):
            bid, rest = m.group(1), m.group(2)
            ctor = re.search(r"([A-Z]\w*)::new", rest)
            if ctor:
                name = ctor.group(1)
                fqn = cls2fqn.get(name) or vanilla.get(name)
                entries[bid] = (fqn, "")
                if not fqn:
                    unresolved.append(f"{bid} (类 {name} 找不到全名)")
                continue
            ctor = re.search(r"new\s+([A-Z]\w*)\s*\(([^)]*)\)", rest)
            if ctor:
                lam = (re.match(r"\s*(\w+)\s*->", rest) or [None, ""])[1]
                name, extras = _extra_args(rest, lam)
                name = name or ctor.group(1)
                fqn = cls2fqn.get(name) or vanilla.get(name)
                entries[bid] = (fqn, ",".join(extras))
                if not fqn:
                    unresolved.append(f"{bid} (类 {name} 找不到全名)")
                continue
            unresolved.append(f"{bid}（注册写法没识别：{rest.strip()[:40]}）")
        for m in re.finditer(r'(?<![\w.])(simple|ore)\s*\(\s*"([a-z0-9_]+)"', t):
            cls = "WanderliteOreBlock" if m.group(1) == "ore" else "Block"
            entries.setdefault(m.group(2), (cls2fqn.get(cls) or vanilla.get(cls), ""))
    return entries, unresolved


def class_fingerprint(project):
    """编译产物指纹：class 文件内容变了（方块属性多半跟着变）就要重跑探针。"""
    classes = os.path.join(project, "build", "classes", "java", "main")
    if not os.path.isdir(classes):
        return None
    entries = []
    for dp, _, fs in os.walk(classes):
        for f in fs:
            if f.endswith(".class"):
                p = os.path.join(dp, f)
                rel = os.path.relpath(p, classes).replace("\\", "/")
                entries.append((rel, hashlib.sha256(open(p, "rb").read()).hexdigest()))
    entries.sort()
    h = hashlib.sha256()
    for rel, digest in entries:
        h.update(rel.encode("utf-8"))
        h.update(digest.encode("ascii"))
    return h.hexdigest()[:32]


# ---------------------------------------------------------------- 主流程

def run_probe(project, verbose=True):
    javac, java = find_java()
    if not javac:
        return None, "找不到 javac/java（设 JAVA_HOME 或加进 PATH）"
    cp, err = build_classpath(project)
    if err:
        return None, err

    entries, unresolved = block_class_map(project)
    if not entries:
        return None, "没有从源码里解析出任何方块注册点"
    work = os.path.join(project, "build", "tools", "blockstate_probe")
    os.makedirs(work, exist_ok=True)
    src = os.path.join(work, "BlockStateProbe.java")
    open(src, "w", encoding="utf-8", newline="\n").write(JAVA_SRC)
    manifest = os.path.join(work, "manifest.txt")
    with open(manifest, "w", encoding="utf-8", newline="\n") as fh:
        for bid in sorted(entries):
            fqn, extra = entries[bid]
            if not fqn:
                continue
            fh.write(f"{bid}\t{fqn}\t{extra}\n" if extra else f"{bid}\t{fqn}\n")
    out_json = os.path.join(work, "raw.json")
    classes_out = os.path.join(work, "classes")
    os.makedirs(classes_out, exist_ok=True)
    cps = os.pathsep.join(cp)
    r = subprocess.run([javac, "-nowarn", "-cp", cps, "-d", classes_out, src],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        return None, "javac 失败:\n" + (r.stderr or r.stdout or "")[:2000]
    r = subprocess.run([java, "-Xmx1500m", "-cp", os.pathsep.join([classes_out, cps]),
                        "BlockStateProbe", manifest, out_json],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if not os.path.isfile(out_json):
        return None, "探针没有产出结果:\n" + (r.stderr or r.stdout or "")[:2000]
    data = json.load(open(out_json, encoding="utf-8"))
    done = data.pop("__done", {})
    if done.get("ok", 0) == 0:
        return None, f"探针一个方块都没探到（fail={done.get('fail')}）:\n" + (r.stderr or "")[:2000]
    if verbose:
        print(f"探针：ok={done.get('ok')} fail={done.get('fail')}"
              f"{'（bootstrap 部分失败：' + done.get('bootstrapError', '') + '）' if done.get('bootstrapError') else ''}")
        if unresolved:
            print("  没解析出类名的注册点（这些方块会缺属性数据）: " + "; ".join(unresolved))
    snapshot = {
        "mcVersion": "1.21.1",
        "neoForge": "21.1.251",
        "generator": "tools/probe_blockstates.py",
        "classFingerprint": class_fingerprint(project),
        "blockCount": len(data),
        "failCount": done.get("fail", 0),
        "bootstrapError": done.get("bootstrapError", ""),
        "unresolved": unresolved,
        "blocks": data,
    }
    return snapshot, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", default="clockwork_unoffical")
    ap.add_argument("--check", action="store_true", help="只比对快照指纹与当前编译产物")
    args = ap.parse_args()
    project = os.path.join(ROOT, args.project)

    if args.check:
        if not os.path.isfile(SNAPSHOT):
            print("快照不存在: " + SNAPSHOT)
            return 1
        snap = json.load(open(SNAPSHOT, encoding="utf-8"))
        now = class_fingerprint(project)
        old = snap.get("classFingerprint")
        if now is None:
            print("没有编译产物，无法比对指纹（快照仍可用）")
            return 0
        if now != old:
            print(f"快照已过期：{old} -> {now}，请重跑 python tools/probe_blockstates.py")
            return 1
        print(f"快照是最新的（指纹 {now}，{snap.get('blockCount')} 个方块）")
        return 0

    snapshot, err = run_probe(project)
    if err:
        print("探测失败：" + err)
        return 1
    tmp = SNAPSHOT + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(snapshot, fh, indent=1, ensure_ascii=False, sort_keys=True)
        fh.write("\n")
    os.replace(tmp, SNAPSHOT)
    print(f"已写入 {SNAPSHOT}（{snapshot['blockCount']} 个方块，指纹 {snapshot['classFingerprint']}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
