#!/usr/bin/env python3
"""Clockwork 资源校验的公共库（check_assets.py / selftest_assets.py / 诊断脚本共用）。

这里集中三件容易写错的事：

1. **贴图存在性必须跨命名空间查**。模型里 `"textures": {"1": "create:block/axis"}` 这种
   引用要去 Create 的 jar 里找，只在 `assets/vs_clockwork/` 下找会误报（第一版脚本就这么错过）。
   查找顺序：本模组磁盘资源 → 本模组 build/libs jar → libs/*.jar → 实例 mods/*.jar → 原版
   `client-<ver>-extra.jar`（**原版模型/贴图在 -extra.jar 里，不在 -srg.jar，也不在 assets/indexes**
   ——这条是实测出来的：`assets/indexes/17.json` 只有字体/语言/音效，没有 models/textures）。

2. **1.21.1 对「贴图键解析不出来」是完全静默的**（黑紫直接上屏，日志一行都没有）。
   依据：client-1.21.1 jar 的常量池里，`SpriteLoader` 没有任何 missing/sprite 文案，
   `ModelBakery` 只有 "Unable to load model"/"Unable to bake model" 两句
   （`tools/dump_class_strings.py` 可复现）。所以必须静态检查 `#键` 能不能沿 parent 链解析。

3. **`texture_size` 不是 Minecraft 的字段**（1.20.1 与 1.21.1 的 client jar 里都搜不到这个字符串，
   `tools/scan_jar_bytes.py` 可复现），它只是 Blockbench 的标注，两个版本都会被忽略，
   因此它不是「旧版能显示、新版黑紫」的原因——这一点排除掉可以少走弯路。
"""
import json
import os
import re
import struct
import zipfile

NS = "vs_clockwork"
VALID_PATH = re.compile(r"^[a-z0-9_\-./]+$")
MC_DEFAULT = r"C:\Users\yql11\OneDrive\Desktop\.minecraft"
VANILLA_EXTRA = os.path.join(
    MC_DEFAULT, "libraries", "net", "minecraft", "client",
    "1.21.1-20240808.144430", "client-1.21.1-20240808.144430-extra.jar")


def split(loc):
    return tuple(loc.split(":", 1)) if ":" in loc else ("minecraft", loc)


# 代码里注册的内置模型（不是文件）。builtin/generated 在 1.21.1 由 ModelBakery 现算：
# **只有 layer0 是必需的**（layer1..layer5 缺省就是不渲染那一层，原版药水/皮革甲才写满 6 层），
# 所以合成的 elements 只引用 #layer0 —— 这样「物品模型忘记写 layer0」会被查出来，
# 而只写一层贴图的正常物品不会被误报。
BUILTIN_MODELS = {
    "builtin/generated": {"elements": [{"faces": {"north": {"texture": "#layer0"}}}]},
    "builtin/entity": {},
    "builtin/missing": {},
}


def png_size_bytes(data):
    """从字节里读 PNG 宽高；失败返回 (0, 0)。"""
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return 0, 0
    w, h = struct.unpack(">II", data[16:24])
    return w, h


class Assets:
    """资源解析器：模型 / 贴图 / mcmeta 在磁盘与各种 jar 里的统一入口。"""

    def __init__(self, project, mc_root=MC_DEFAULT, extra_jars=()):
        self.project = project
        self.assets = os.path.join(project, "src", "main", "resources", "assets", NS)
        self.res = os.path.dirname(self.assets)              # .../assets
        self.gen_res = os.path.join(project, "src", "generated", "resources", "assets")
        self.mc_root = mc_root
        self._jars = {}
        self._tex_index = None
        for j in extra_jars:
            self._open_jar(j)
        libs = os.path.join(project, "libs")
        if os.path.isdir(libs):
            for f in sorted(os.listdir(libs)):
                if f.endswith(".jar"):
                    self._open_jar(os.path.join(libs, f))
        mods = os.path.join(mc_root, "versions", "1.21.1-NeoForge_21.1.251", "mods")
        if os.path.isdir(mods):
            for f in sorted(os.listdir(mods)):
                if f.endswith(".jar"):
                    self._open_jar(os.path.join(mods, f))
        if os.path.isfile(VANILLA_EXTRA):
            self._open_jar(VANILLA_EXTRA)
        self._model_cache = {}

    # ---------- jar ----------
    def _open_jar(self, path):
        if path in self._jars or not os.path.isfile(path):
            return
        try:
            self._jars[path] = zipfile.ZipFile(path)
        except Exception:  # noqa: BLE001
            self._jars[path] = None

    def _textures(self):
        """{(ns, path): jar 路径} —— 所有 jar 里的贴图索引。"""
        if self._tex_index is None:
            idx = {}
            for path, z in self._jars.items():
                if z is None:
                    continue
                for n in z.namelist():
                    if n.startswith("assets/") and "/textures/" in n and n.endswith(".png"):
                        rest = n[len("assets/"):]
                        ns, tail = rest.split("/", 1)
                        idx.setdefault((ns, tail[len("textures/"):-4]), path)
            self._tex_index = idx
        return self._tex_index

    # ---------- 读取 ----------
    def _read_disk(self, res_root, ns, rel):
        fp = os.path.join(res_root, ns, rel)
        if os.path.isfile(fp):
            return open(fp, "rb").read()
        return None

    def read(self, ns, kind, path, ext=""):
        rel = f"{kind}/{path}{ext}"
        for root in (self.res, self.gen_res):
            data = self._read_disk(root, ns, rel)
            if data is not None:
                return data
        name = f"assets/{ns}/{rel}"
        for z in self._jars.values():
            if z is None:
                continue
            try:
                if name in z.namelist():
                    return z.read(name)
            except Exception:  # noqa: BLE001
                continue
        return None

    def model(self, model_id):
        if model_id in self._model_cache:
            return self._model_cache[model_id]
        ns, path = split(model_id)
        if ns == "minecraft" and path in BUILTIN_MODELS:
            # builtin/* 是**代码里注册**的模型，不是文件（证据：client-1.21.1 jar 的
            # ModelBakery 常量池里有 "builtin/generated" 与 "builtin/missing"）。
            # builtin/generated 用 layer0..layer5 六张层贴图，这里合成出同构的 elements，
            # 这样「忘记写 layer0」也能被查出来。
            res = (BUILTIN_MODELS[path], None)
            self._model_cache[model_id] = res
            return res
        raw = self.read(ns, "models", path, ".json")
        if raw is None:
            res = (None, f"模型文件不存在: assets/{ns}/models/{path}.json")
        else:
            try:
                res = (json.loads(raw.decode("utf-8")), None)
            except Exception as exc:  # noqa: BLE001
                res = (None, f"JSON 解析失败: {exc}")
        self._model_cache[model_id] = res
        return res

    def model_on_disk(self, ns, path):
        for root in (self.res, self.gen_res):
            fp = os.path.join(root, ns, "models", path + ".json")
            if os.path.isfile(fp):
                return fp
        return None

    def texture_exists(self, ref):
        ns, path = split(ref)
        if self._read_disk(self.res, ns, f"textures/{path}.png") is not None:
            return True
        if self._read_disk(self.gen_res, ns, f"textures/{path}.png") is not None:
            return True
        return (ns, path) in self._textures()

    def texture_bytes(self, ref):
        ns, path = split(ref)
        for root in (self.res, self.gen_res):
            data = self._read_disk(root, ns, f"textures/{path}.png")
            if data is not None:
                return data
        name = f"assets/{ns}/textures/{path}.png"
        for z in self._jars.values():
            if z is None:
                continue
            try:
                if name in z.namelist():
                    return z.read(name)
            except Exception:  # noqa: BLE001
                continue
        return None

    # ---------- parent 链 ----------
    def chain(self, model_id, _seen=None):
        """返回 (merged_textures, elements, errors)。textures 里存 (值, 定义它的模型)。"""
        tex, elements, errs = {}, None, []
        seen = set()
        cur = model_id
        while cur is not None:
            if cur in seen:
                errs.append(f"parent 形成环: {cur}")
                break
            seen.add(cur)
            data, err = self.model(cur)
            if err is not None:
                errs.append(f"{cur}: {err}")
                break
            for k, v in (data.get("textures") or {}).items():
                if isinstance(v, str):
                    tex.setdefault(k, (v, cur))
            if isinstance(data.get("elements"), list) and elements is None:
                elements = data["elements"]
            p = data.get("parent")
            cur = p if isinstance(p, str) else None
        return tex, elements, errs

    def face_keys(self, model_id):
        """(tex, 未解析的 #键 列表, elements, errs)"""
        tex, elements, errs = self.chain(model_id)
        bad = []
        for k in sorted(used_keys(elements)):
            ref, why = resolve_key(k, tex)
            if ref is None:
                bad.append((k, why))
        return tex, bad, elements, errs


def resolve_key(key, tex, depth=0):
    if depth > 16:
        return None, "引用链过深（疑似环）"
    if not key.startswith("#"):
        return key, ""
    name = key[1:]
    if name not in tex:
        return None, f'textures 里没有键 "{name}"（含父模型链）'
    val = tex[name][0]
    if val.startswith("#"):
        return resolve_key(val, tex, depth + 1)
    return val, ""


def used_keys(elements):
    keys = set()
    for el in elements or []:
        for face in (el.get("faces") or {}).values():
            t = face.get("texture")
            if isinstance(t, str) and t.startswith("#"):
                keys.add(t)
    return keys


def iter_models(assets_dir):
    """产出 (相对 assets 的路径如 'models/block/x.json', 绝对路径)。"""
    for dp, _, fs in os.walk(os.path.join(assets_dir, "models")):
        for f in fs:
            if f.endswith(".json"):
                p = os.path.join(dp, f)
                yield os.path.relpath(p, assets_dir).replace("\\", "/"), p


# ============================== 各项检查 ==============================

def check_blockstates(A):
    """blockstate 里引用的模型（含 parent 链）能否解析 —— 对应日志的 missing model for variant。"""
    bad = []
    bs_dir = os.path.join(A.assets, "blockstates")
    if not os.path.isdir(bs_dir):
        return [f"缺少目录 {bs_dir}"]
    for name in sorted(os.listdir(bs_dir)):
        if not name.endswith(".json"):
            continue
        rel = f"blockstates/{name}"
        try:
            data = json.load(open(os.path.join(bs_dir, name), encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001
            bad.append(f"{rel}: JSON 解析失败 {exc}")
            continue
        refs = []
        for variant, v in (data.get("variants") or {}).items():
            for one in (v if isinstance(v, list) else [v]):
                if isinstance(one, dict) and isinstance(one.get("model"), str):
                    refs.append((one["model"], variant))
        for part in (data.get("multipart") or []):
            ap = part.get("apply")
            for one in (ap if isinstance(ap, list) else [ap]):
                if isinstance(one, dict) and isinstance(one.get("model"), str):
                    refs.append((one["model"], part.get("when")))
        if not refs:
            bad.append(f"{rel}: 没有解析出任何模型引用（结构异常？）")
        for model_id, variant in refs:
            mid = model_id if ":" in model_id else f"{NS}:{model_id}"
            _, _, errs = A.chain(mid)
            if errs:
                bad.append(f"{rel}: 变体 {variant!r} 模型链断了 -> {'; '.join(errs)}")
    return bad


def check_model_keys(A):
    """所有模型（含父链继承的 elements）里的 `#键` 是否都能解析。不能 = 静默黑紫。"""
    bad = []
    for rel, _fp in sorted(iter_models(A.assets)):
        mid = f"{NS}:" + rel[len("models/"):-len(".json")]
        data, err = A.model(mid)
        if err is not None:
            bad.append(f"{rel}: {err}")
            continue
        if data.get("loader") == "neoforge:obj":
            continue                      # OBJ 的贴图键由 mtl 的 map_Kd 引用，另有检查
        _, unresolved, _, errs = A.face_keys(mid)
        bad.extend(f"{rel}: 模型链断了 -> {e}" for e in errs)
        for key, why in unresolved:
            bad.append(f"{rel}: 面引用 {key} 无法解析（{why}）")
    return bad


def check_texture_refs(A):
    """模型里所有**具体**贴图引用（含 create:/minecraft: 等命名空间）是否真的存在。"""
    bad = []
    for rel, _fp in sorted(iter_models(A.assets)):
        mid = f"{NS}:" + rel[len("models/"):-len(".json")]
        tex, _, errs = A.chain(mid)
        if errs:
            continue                      # 链断了由 [7] 报
        for key, (ref, _src) in sorted(tex.items()):
            if not isinstance(ref, str) or ref.startswith("#"):
                continue
            if ":" not in ref:
                # 无命名空间 = minecraft:（原版语义）。只有原版里也找不到才算缺失，
                # 否则会误报 `"particle": "block/iron_block"` 这种完全合法的原生贴图。
                if not A.texture_exists(f"minecraft:{ref}"):
                    bad.append(f'{rel}: textures["{key}"]="{ref}" 缺命名空间，'
                               f'按 minecraft:{ref} 解析后也不存在')
                continue
            if not A.texture_exists(ref):
                hint = ""
                ns, path = split(ref)
                if ns == NS:
                    for alt in (path.replace("block/", "blocks/"),
                                path.replace("blocks/", "block/"),
                                path + "_top"):
                        if A.texture_exists(f"{ns}:{alt}"):
                            hint = f"（但 {ns}:{alt} 存在，疑似路径写错）"
                            break
                bad.append(f'{rel}: textures["{key}"] -> {ref}.png 不存在{hint}')
    return bad


def check_pngs(A):
    bad = []
    root = os.path.join(A.assets, "textures")
    for dp, _, fs in os.walk(root):
        for f in fs:
            if not f.endswith(".png"):
                continue
            fp = os.path.join(dp, f)
            rel = os.path.relpath(fp, A.assets).replace("\\", "/")
            size = os.path.getsize(fp)
            if size == 0:
                bad.append(f"{rel}: 0 字节")
                continue
            w, h = png_size_bytes(open(fp, "rb").read())
            if w == 0 or h == 0:
                bad.append(f"{rel}: 不是合法 PNG（magic/IHDR 异常）")
    return bad


def check_mcmeta(A):
    """.png.mcmeta 的动画定义是否自洽。

    正确语义（1.21.1 `AnimationMetadataSectionSerializer` + `AnimationMetadataSection
    .calculateFrameSize`，字段名在常量池里可直接看到：frametime / frames / index / time /
    width / height / interpolate）：
      - 帧尺寸由 `animation.width`/`height` 决定，缺省时退化为「正方形，边长 = min(宽,高)」；
        只写 width 时帧高 = 整张贴图高度（即横向排帧）。
      - 帧总数 = (贴图宽/帧宽) * (贴图高/帧高)，`frames` 里的整数是**帧序号**，不是文件名。
    所以判定 = 帧序号落在帧总数内 + 贴图尺寸能被帧尺寸整除。
    （第一版脚本按旧版 `_0.png/_1.png` 文件语义检查，制造了 16 条假问题；第二版假设帧一定是
     正方形，又误报了 48x128 这种 width/height 声明的贴图。两版都已在注释里留痕。）
    """
    bad = []
    for dp, _, fs in os.walk(A.assets):
        for f in fs:
            if not f.endswith(".png.mcmeta"):
                continue
            fp = os.path.join(dp, f)
            rel = os.path.relpath(fp, A.assets).replace("\\", "/")
            png = fp[:-len(".mcmeta")]
            if not os.path.isfile(png):
                bad.append(f"{rel}: 对应的 {os.path.basename(png)} 不存在")
                continue
            try:
                data = json.load(open(fp, encoding="utf-8"))
            except Exception as exc:  # noqa: BLE001
                bad.append(f"{rel}: JSON 解析失败 {exc}")
                continue
            if not isinstance(data, dict):
                bad.append(f"{rel}: 顶层必须是对象（旧版 mcmeta 结构？）")
                continue
            anim = data.get("animation")
            if anim is None:
                continue
            if not isinstance(anim, dict):
                bad.append(f'{rel}: "animation" 必须是对象')
                continue
            ft = anim.get("frametime")
            if ft is not None and (isinstance(ft, bool) or not isinstance(ft, int) or ft < 1):
                bad.append(f"{rel}: frametime 必须是 >=1 的整数（当前 {ft!r}）")
            w, h = png_size_bytes(open(png, "rb").read())
            if w == 0:
                continue                       # PNG 本身的问题由 [9] 报
            fw = anim.get("width", -1)
            fh = anim.get("height", -1)
            if fw != -1 and (not isinstance(fw, int) or fw <= 0):
                bad.append(f"{rel}: width 非法 -> {fw!r}")
                continue
            if fh != -1 and (not isinstance(fh, int) or fh <= 0):
                bad.append(f"{rel}: height 非法 -> {fh!r}")
                continue
            if fw != -1 and fh != -1:
                frame = (fw, fh)
            elif fw != -1:
                frame = (fw, h)
            elif fh != -1:
                frame = (w, fh)
            else:
                m = min(w, h)
                frame = (m, m)
            if w % frame[0] or h % frame[1]:
                bad.append(f"{rel}: 帧尺寸 {frame[0]}x{frame[1]} 不能整除贴图 {w}x{h}")
                continue
            count = (w // frame[0]) * (h // frame[1])
            frames = anim.get("frames")
            if frames is None:
                continue
            if not isinstance(frames, list) or not frames:
                bad.append(f"{rel}: frames 必须是非空数组")
                continue
            for i, fr in enumerate(frames):
                if isinstance(fr, bool) or not isinstance(fr, (int, dict)):
                    bad.append(f"{rel}: frames[{i}] 类型非法 -> {fr!r}")
                    continue
                idx = fr if isinstance(fr, int) else fr.get("index")
                if not isinstance(idx, int):
                    bad.append(f"{rel}: frames[{i}] 缺 index -> {fr!r}")
                    continue
                if idx < 0 or idx >= count:
                    bad.append(f"{rel}: frames[{i}] 帧序号 {idx} 超出范围（0..{count - 1}，"
                               f"贴图 {w}x{h}，帧 {frame[0]}x{frame[1]}）")
                t = fr.get("time") if isinstance(fr, dict) else None
                if t is not None and (isinstance(t, bool) or not isinstance(t, int) or t < 1):
                    bad.append(f"{rel}: frames[{i}] time 非法 -> {t!r}")
    return bad


# ---------- [12] 同一族模型对贴图的 UV 缩放（texel 密度）一致性 ----------
#
# 背景（2026-10-16，气体储罐"成型/拆分贴图跳变"）：
#   Java 版模型的 uv 永远是 0..16 归一化坐标 —— **`texture_size` 字段 Java 版完全不读**
#   （1.21.1 `client-1.21.1-*-extra.jar` 全部 class 的常量池里没有 `texture_size`；
#    `cullface`/`ambientocclusion`/`gui_light`/`tintindex` 都在，属正对照）。
#   所以一面 uv 跨度 d 覆盖的像素数 = d/16 * 贴图实际像素，**与 texture_size 无关**。
#   duct_tank 的病根是：block_single 从 64x64 的 4x4 CT 图集里取 1 格（uv 跨度 4 = 16px
#   → 1 px/单位），而 block_bottom/top/middle 把整张 64x64 贴图铺满一个面（uv 跨度 16 = 64px
#   → 4 px/单位）：同一族里差 4 倍，成型/拆分时纹理会明显跳变。
#
# 判据：blockstate 里的各个变体正是"运行时会互相切换"的模型，它们对贴图的 texel 密度
# 应当一致。这里只做**提示**（动画/发光/细节贴图可以合法地使用别的密度，见报告清单），
# 不计入硬失败项。
UV_TOL = 0.02        # 聚类容差：同一贴图/同一模型的众数密度用 2%（Blockbench box-UV 取整）
UV_MODEL_TOL = 0.10  # 变体模型之间允许 10% 偏差（同前；>1.2 倍才算真跳变）
UV_JUMP = 1.5       # 同一族内不同贴图的密度比超过这个倍数 = 换模型时贴图会跳变
_FACE_SPAN = {"north": (0, 1), "south": (0, 1), "east": (2, 1),
              "west": (2, 1), "up": (0, 2), "down": (0, 2)}


def texture_frame_size(A, ref):
    """贴图的有效帧尺寸：动画贴图按 mcmeta 的帧尺寸（不是整条带）。
    帧尺寸语义与 check_mcmeta 一致（width/height 缺省时退化为 min(w,h) 正方形帧）。"""
    data = A.texture_bytes(ref)
    if data is None:
        return None
    w, h = png_size_bytes(data)
    if w == 0 or h == 0:
        return None
    ns, path = split(ref)
    meta = A.read(ns, "textures", path + ".png", ".mcmeta")
    if meta:
        try:
            anim = (json.loads(meta.decode("utf-8")) or {}).get("animation")
        except Exception:  # noqa: BLE001
            anim = None
        if isinstance(anim, dict):
            fw, fh = anim.get("width"), anim.get("height")
            if isinstance(fw, int) and not isinstance(fw, bool) and fw > 0:
                if isinstance(fh, int) and not isinstance(fh, bool) and fh > 0:
                    if w % fw == 0 and h % fh == 0:
                        return fw, fh
                elif w % fw == 0:
                    return fw, h
            elif isinstance(fh, int) and not isinstance(fh, bool) and fh > 0 and h % fh == 0:
                return w, fh
            m = min(w, h)
            if w % m == 0 and h % m == 0:
                return m, m
    return w, h


def _face_density(el, face_dir, face, tex_size):
    uv = face.get("uv")
    if not isinstance(uv, list) or len(uv) != 4:
        return None
    if any(isinstance(v, bool) or not isinstance(v, (int, float)) for v in uv):
        return None
    ax, ay = _FACE_SPAN[face_dir]
    a = el.get("from") or [0, 0, 0]
    b = el.get("to") or [16, 16, 16]
    fw, fh = abs(b[ax] - a[ax]), abs(b[ay] - a[ay])
    if fw <= 0 or fh <= 0:
        return None
    du = abs(uv[2] - uv[0]) / 16.0 * tex_size[0] / fw
    dv = abs(uv[3] - uv[1]) / 16.0 * tex_size[1] / fh
    if face.get("rotation") in (90, 270):        # 旋转面：uv 的 u/v 与面的宽/高互换
        du, dv = dv, du
    return du, dv


def _same(a, b):
    return (abs(a[0] - b[0]) <= UV_TOL * max(a[0], b[0], 1e-9)
            and abs(a[1] - b[1]) <= UV_TOL * max(a[1], b[1], 1e-9))


def _modal(items):
    """按 2% 容差聚类，返回最大簇（同一张贴图/同一个模型的众数密度）。"""
    clusters = []
    for it in items:
        for c in clusters:
            if _same(c[0][0], it[0]):
                c.append(it)
                break
        else:
            clusters.append([it])
    return max(clusters, key=len)


def _same_tol(a, b, tol):
    return (abs(a[0] - b[0]) <= tol * max(a[0], b[0], 1e-9)
            and abs(a[1] - b[1]) <= tol * max(a[1], b[1], 1e-9))


def check_uv_scaling(A):
    """[13] 同一 blockstate 的各变体模型（= 运行时互相切换的一族）对贴图的 texel 密度是否一致。

    见本节顶部注释：uv 是 0..16 归一化坐标，texture_size 无效，
    密度 = uv跨度/16 * 贴图像素 / 面尺寸。
      (a) 同一张贴图被**不同变体模型**以不同缩放使用（容差 UV_MODEL_TOL）——
          同一模型内部不比较：Blockbench box-UV 允许整像素取整，也允许故意复用贴图局部。
      (b) 同一族内**不同贴图**的密度相差超过 UV_JUMP 倍 —— 这正是「成型/拆分切换模型时贴图跳变」
          （duct_tank 2026-10-16 的毛病：单块 1 px/单位 vs 多层 4 px/单位）。
    """
    bad = []
    bs_dir = os.path.join(A.assets, "blockstates")
    if not os.path.isdir(bs_dir):
        return [f"缺少目录 {bs_dir}"]
    for name in sorted(os.listdir(bs_dir)):
        if not name.endswith(".json"):
            continue
        rel = f"blockstates/{name}"
        try:
            data = json.load(open(os.path.join(bs_dir, name), encoding="utf-8"))
        except Exception:  # noqa: BLE001
            continue                                  # JSON 坏了由 [4] 报
        refs = []
        for variant, v in (data.get("variants") or {}).items():
            for one in (v if isinstance(v, list) else [v]):
                if isinstance(one, dict) and isinstance(one.get("model"), str):
                    refs.append(one["model"])
        for part in (data.get("multipart") or []):
            ap = part.get("apply")
            for one in (ap if isinstance(ap, list) else [ap]):
                if isinstance(one, dict) and isinstance(one.get("model"), str):
                    refs.append(one["model"])

        fam = {}                    # 贴图引用 -> {模型 id: [(密度, 出处)]}
        for model_id in refs:
            mid = model_id if ":" in model_id else f"{NS}:{model_id}"
            tex, elements, _ = A.chain(mid)
            for el in elements or []:
                for face_dir, face in (el.get("faces") or {}).items():
                    if face_dir not in _FACE_SPAN:
                        continue
                    ref, _ = resolve_key(face.get("texture") or "", tex)
                    if not ref:
                        continue
                    size = texture_frame_size(A, ref)
                    if not size:
                        continue
                    d = _face_density(el, face_dir, face, size)
                    if d is None:
                        continue
                    fam.setdefault(ref, {}).setdefault(mid, []).append(
                        (d, f"{model_id} {el.get('name', '?')}/{face_dir} uv={face.get('uv')} "
                            f"面={size[0]}x{size[1]}"))
        if not fam:
            continue

        # (a) 同一贴图、不同变体模型之间缩放不一致
        for ref, by_model in sorted(fam.items()):
            dom = {m: _modal(items)[0][0] for m, items in by_model.items()}
            if len(dom) < 2:
                continue
            clusters = []
            for m, d in sorted(dom.items()):
                for c in clusters:
                    if _same_tol(c[0][1], d, UV_MODEL_TOL):
                        c.append((m, d))
                        break
                else:
                    clusters.append([(m, d)])
            clusters.sort(key=len, reverse=True)
            for c in clusters[1:]:
                m, d = c[0]
                bad.append(f"{rel}: 同一贴图 {ref} 被不同变体以不同缩放使用 —— 多数变体 "
                           f"{clusters[0][0][1][0]:.3f}x{clusters[0][0][1][1]:.3f} px/单位，"
                           f"{m} 是 {d[0]:.3f}x{d[1]:.3f}")

        # (b) 同一族内跨贴图密度跳变（成型/拆分切换模型时会看到的跳变）
        if len(fam) < 2:
            continue
        dom = {}
        for ref, by_model in fam.items():
            items = [it for v in by_model.values() for it in v]
            best = _modal(items)
            dom[ref] = (best[0][0], len(best), len(items))
        us = [v[0][0] for v in dom.values()]
        vs = [v[0][1] for v in dom.values()]
        if min(us) > 0 and min(vs) > 0 and (max(us) / min(us) > UV_JUMP or max(vs) / min(vs) > UV_JUMP):
            detail = "; ".join(
                f"{r} {d[0][0]:.3f}x{d[0][1]:.3f}({d[1]}/{d[2]}面)" for r, d in sorted(dom.items()))
            bad.append(f"{rel}: 变体模型之间 UV 缩放差 {max(us) / min(us):.2f}x/{max(vs) / min(vs):.2f}x "
                       f"（切换模型时贴图会跳变）—— {detail}")
    return bad


def check_obj(A):
    bad = []
    for rel, fp in sorted(iter_models(A.assets)):
        try:
            data = json.load(open(fp, encoding="utf-8"))
        except Exception:  # noqa: BLE001
            continue
        loader = data.get("loader")
        if isinstance(loader, str) and not loader.startswith(("neoforge:", "minecraft:")):
            bad.append(f"{rel}: 非法 loader '{loader}'（1.21.1 只认 neoforge:obj）")
        if loader != "neoforge:obj":
            continue
        model = data.get("model")
        if not isinstance(model, str):
            bad.append(f"{rel}: neoforge:obj 缺少 model")
            continue
        ns, mp = split(model)
        raw = A.read(ns, "", "", "")
        obj_full = None
        for root in (A.res, A.gen_res):
            cand = os.path.join(root, ns, mp)
            if os.path.isfile(cand):
                obj_full = cand
                break
        if obj_full is None:
            cand = os.path.join(A.res, ns, mp)
            if not os.path.isfile(cand):
                # 也允许放在 jar 里（assets/<ns>/<mp>）
                found = False
                for z in A._jars.values():
                    if z is not None and f"assets/{ns}/{mp}" in z.namelist():
                        found = True
                        break
                if not found:
                    bad.append(f"{rel}: .obj 不存在 -> assets/{ns}/{mp}")
                    continue
        model_id = rel[len("models/"):-len(".json")]
        mtls, used = [], set()
        text = ""
        if obj_full:
            text = open(obj_full, encoding="utf-8", errors="replace").read()
            for line in text.splitlines():
                s = line.strip()
                if s.startswith("mtllib"):
                    mtls.append(s.split(None, 1)[1].strip())
                elif s.startswith("usemtl"):
                    used.add(s.split(None, 1)[1].strip())
        override = data.get("mtl_override")
        if override:
            mtls = [override]
        if used and not mtls:
            bad.append(f"{rel}: obj 用了 usemtl 但没有 mtllib")
        tex, _, _ = A.chain(f"{NS}:{model_id}")
        for mt in mtls:
            if ":" in mt:
                mns, mpath = split(mt)
            else:
                mns = ns
                mpath = (mp.rsplit("/", 1)[0] + "/" if "/" in mp else "") + mt
            mtl_txt = None
            for root in (A.res, A.gen_res):
                cand = os.path.join(root, mns, mpath)
                if os.path.isfile(cand):
                    mtl_txt = open(cand, encoding="utf-8", errors="replace").read()
                    break
            if mtl_txt is None:
                for z in A._jars.values():
                    if z is not None and f"assets/{mns}/{mpath}" in z.namelist():
                        mtl_txt = z.read(f"assets/{mns}/{mpath}").decode("utf-8", "replace")
                        break
            if mtl_txt is None:
                bad.append(f"{rel}: mtllib '{mt}' 不存在 -> assets/{mns}/{mpath}")
                continue
            mats = set(re.findall(r"^newmtl\s+(\S+)", mtl_txt, re.M))
            for u in sorted(used - mats):
                bad.append(f"{rel}: usemtl {u} 在 {mt} 里未定义")
            for key in sorted(set(re.findall(r"^map_Kd\s+(\S+)", mtl_txt, re.M))):
                if not key.startswith("#"):
                    bad.append(f"{rel}: MTL map_Kd {key} 不是 '#纹理键' 形式（OBJ loader 要求 #键名）")
                    continue
                name = key[1:]
                if name not in tex:
                    bad.append(f'{rel}: MTL map_Kd {key} 但模型 textures 里没有键 "{name}"')
                    continue
                ref = tex[name][0]
                if not A.texture_exists(ref):
                    bad.append(f"{rel}: MTL map_Kd {key} -> {ref}.png 不存在")
    return bad


def check_invalid_paths(A):
    bad = []
    for dp, _, fs in os.walk(A.assets):
        for f in fs:
            rel = os.path.relpath(os.path.join(dp, f), A.assets).replace("\\", "/")
            if f.endswith(".bbmodel"):
                continue          # build.gradle 已 exclude
            if not VALID_PATH.match(rel):
                bad.append(f"{rel}: 路径含非法字符（1.21 资源包校验会静默忽略该文件）")
    return bad


def check_jar_vs_source(A, jar):
    """产物 jar 的 assets/vs_clockwork 与源码资源树是否一致 + jar 结构自检。"""
    bad = []
    if not os.path.isfile(jar):
        return [f"jar 不存在: {jar}"]
    z = zipfile.ZipFile(jar)
    names = [n for n in z.namelist() if n.startswith(f"assets/{NS}/") and not n.endswith("/")]
    disk = set()
    for root in (os.path.join(A.project, "src", "main", "resources", "assets"),
                 os.path.join(A.project, "src", "generated", "resources", "assets")):
        if not os.path.isdir(root):
            continue
        for dp, _, fs in os.walk(os.path.join(root, NS)):
            for f in fs:
                if f.endswith(".bbmodel"):
                    continue
                rel = os.path.relpath(os.path.join(dp, f), root).replace("\\", "/")
                disk.add(f"assets/{rel}")
    for m in sorted(disk - set(names)):
        bad.append(f"jar 里缺资源（打包遗漏）: {m}")
    for m in sorted(set(names) - disk):
        bad.append(f"jar 里多出资源（源码里没有）: {m}")
    for i in z.infolist():
        if i.filename.startswith(f"assets/{NS}/") and i.file_size == 0 and not i.is_dir():
            bad.append(f"jar 里 0 字节条目: {i.filename}")
    fails = 0
    for i in z.infolist():
        if i.is_dir():
            continue
        try:
            with z.open(i) as fh:
                while fh.read(1 << 16):
                    pass
        except Exception as exc:  # noqa: BLE001
            fails += 1
            if fails <= 5:
                bad.append(f"jar 条目读取失败 {i.filename}: {exc}")
    if fails > 5:
        bad.append(f"jar 条目读取失败共 {fails} 条")
    return bad
