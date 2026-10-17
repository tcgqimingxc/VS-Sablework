"""极简 NBT 读取器：把 playerdata/*.dat 里的物品栏（含数据组件）打印成可读文本。

用途：取证「漫游之杖到底写没写进选区数据」——直接读存档里的 ItemStack 组件，不依赖游戏内观察。
用法：python nbt_dump.py <file.dat>  [--path Inventory]
"""
import gzip
import struct
import sys

TAG_NAMES = {
    0: "End", 1: "Byte", 2: "Short", 3: "Int", 4: "Long", 5: "Float",
    6: "Double", 7: "ByteArray", 8: "String", 9: "List", 10: "Compound", 11: "IntArray", 12: "LongArray",
}


class Reader:
    def __init__(self, data):
        self.data = data
        self.i = 0

    def u1(self):
        v = self.data[self.i]
        self.i += 1
        return v

    def i1(self):
        v = struct.unpack_from(">b", self.data, self.i)[0]
        self.i += 1
        return v

    def i2(self):
        v = struct.unpack_from(">h", self.data, self.i)[0]
        self.i += 2
        return v

    def u2(self):
        v = struct.unpack_from(">H", self.data, self.i)[0]
        self.i += 2
        return v

    def i4(self):
        v = struct.unpack_from(">i", self.data, self.i)[0]
        self.i += 4
        return v

    def i8(self):
        v = struct.unpack_from(">q", self.data, self.i)[0]
        self.i += 8
        return v

    def f4(self):
        v = struct.unpack_from(">f", self.data, self.i)[0]
        self.i += 4
        return v

    def f8(self):
        v = struct.unpack_from(">d", self.data, self.i)[0]
        self.i += 8
        return v

    def string(self):
        n = self.u2()
        s = self.data[self.i:self.i + n].decode("utf-8", "replace")
        self.i += n
        return s

    def payload(self, tag_id):
        if tag_id == 1:
            return self.i1()
        if tag_id == 2:
            return self.i2()
        if tag_id == 3:
            return self.i4()
        if tag_id == 4:
            return self.i8()
        if tag_id == 5:
            return self.f4()
        if tag_id == 6:
            return self.f8()
        if tag_id == 7:
            n = self.i4()
            v = list(self.data[self.i:self.i + n])
            self.i += n
            return v
        if tag_id == 8:
            return self.string()
        if tag_id == 9:
            elem = self.u1()
            n = self.i4()
            return (TAG_NAMES[elem], [self.payload(elem) for _ in range(n)])
        if tag_id == 10:
            out = {}
            while True:
                t = self.u1()
                if t == 0:
                    return out
                name = self.string()
                out[name] = self.payload(t)
        if tag_id == 11:
            n = self.i4()
            v = [self.i4() for _ in range(n)]
            return v
        if tag_id == 12:
            n = self.i4()
            v = [self.i8() for _ in range(n)]
            return v
        raise ValueError(f"unknown tag {tag_id} at {self.i}")


def load(path):
    with open(path, "rb") as fh:
        raw = fh.read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    r = Reader(raw)
    t = r.u1()
    assert t == 10, t
    r.string()
    return r.payload(10)


def fmt(v, indent=0, max_list=64):
    pad = "  " * indent
    if isinstance(v, dict):
        if not v:
            return "{}"
        lines = []
        for k, sub in v.items():
            lines.append(f"{pad}{k}: {fmt(sub, indent + 1, max_list)}")
        return "{\n" + "\n".join(lines) + "\n" + pad + "}"
    if isinstance(v, tuple):
        kind, items = v
        if kind == "Compound":
            if len(items) > max_list:
                return f"<{len(items)} compounds>"
            return "[" + ", ".join(fmt(x, indent, max_list) for x in items) + "]"
        return f"{kind}{items}"
    if isinstance(v, list) and len(v) > max_list:
        return f"<{len(v)} values>"
    return repr(v)


def find(node, key):
    """深度优先找第一个叫 key 的子树。"""
    if isinstance(node, dict):
        if key in node:
            return node[key]
        for sub in node.values():
            hit = find(sub, key)
            if hit is not None:
                return hit
    elif isinstance(node, tuple):
        for sub in node[1]:
            hit = find(sub, key)
            if hit is not None:
                return hit
    elif isinstance(node, list):
        for sub in node:
            hit = find(sub, key)
            if hit is not None:
                return hit
    return None


def main():
    path = sys.argv[1]
    root = load(path)
    inv = find(root, "Inventory")
    if inv is None:
        print("no Inventory")
        return
    kind, items = inv
    for idx, item in enumerate(items):
        iid = item.get("id", "?")
        slot = item.get("Slot", "?")
        comps = item.get("components", {})
        print(f"[{idx}] Slot={slot} id={iid} count={item.get('count')}")
        if comps:
            print("     components =", fmt(comps, 6))
        extra = {k: v for k, v in item.items() if k not in ("id", "Slot", "count", "components")}
        if extra:
            print("     extra =", fmt(extra, 6))


if __name__ == "__main__":
    main()
