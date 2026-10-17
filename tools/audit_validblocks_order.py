"""审计辅助脚本（一次性）：核对 Registrate 的 blockEntity(...).validBlocks(X) 声明顺序。

判定规则（依据 Registrate-MC1.21-1.3.0+67.jar 的 javap 输出）：
  BlockEntityBuilder#validBlocks(NonNullSupplier...) 只把 supplier 塞进 Set，
  真正 .get() 发生在之后构建 BlockEntityType 时。因此如果 X 在调用点上还是
  Java 静态字段的"未初始化值"（null），Set 里就会塞进 null，注册期爆炸。

所以：同一类内，X 的声明行必须早于 validBlocks(X) 的调用行。
输出每条调用的 文件:行号 / 参数 / 声明行 / 结论。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "clockwork_unoffical" / "src" / "main" / "java"

# 静态字段声明（含一整条链式初始化，取 `=` 所在行）
DECL_RE = re.compile(
    r"^\s*(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?"
    r"BlockEntry\s*<[^;=]*>\s+([A-Z][A-Z0-9_]*)\s*="
)
CALL_RE = re.compile(r"\.validBlocks\s*\(([^)]*)\)")
BE_RE = re.compile(r"\.blockEntity\s*\(|\.blockEntity\b")


def main() -> int:
    bad = []
    ok = []
    no_valid_blocks = []
    total_calls = 0

    for path in sorted(ROOT.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        if "validBlocks" not in text and not BE_RE.search(text):
            continue
        lines = text.splitlines()

        decls = {}
        for i, line in enumerate(lines, 1):
            m = DECL_RE.match(line)
            if m:
                decls[m.group(1)] = i

        rel = path.relative_to(ROOT.parent.parent)

        # blockEntity 注册点，用于找"没有 validBlocks"的条目
        be_lines = [i for i, l in enumerate(lines, 1) if ".blockEntity(" in l]

        for i, line in enumerate(lines, 1):
            for m in CALL_RE.finditer(line):
                total_calls += 1
                raw = m.group(1).strip()
                args = [a.strip() for a in raw.split(",") if a.strip()]
                for arg in args:
                    name = arg.split(".")[-1].strip()
                    if name not in decls:
                        bad.append((rel, i, arg, decls.get(name), "❌ 同类内找不到声明（可能是别的类的常量）"))
                        continue
                    dline = decls[name]
                    if dline < i:
                        ok.append((rel, i, arg, dline, "✅ 声明在前"))
                    else:
                        bad.append((rel, i, arg, dline, "❌ 声明在使用之后（静态字段此刻为 null）"))

        # 每个 blockEntity(...) 注册块是否带 validBlocks
        if be_lines:
            for start in be_lines:
                chunk = "\n".join(lines[start - 1:start + 14])
                if ".validBlocks(" not in chunk:
                    no_valid_blocks.append((rel, start))

    print(f"validBlocks 调用总数: {total_calls}")
    print(f"参数核对通过: {len(ok)}  有问题: {len(bad)}")
    print()
    print("=== 逐条明细 ===")
    for rel, i, arg, dline, verdict in ok + bad:
        print(f"{rel}:{i}  validBlocks({arg})  声明行 {dline}  {verdict}")
    print()
    print(f"=== ❌/⚠️ 汇总（{len(bad)} 条）===")
    for row in bad:
        print("  ", row)
    print()
    print(f"=== 未调用 validBlocks 的 .blockEntity( 注册点（{len(no_valid_blocks)} 条，仅提示）===")
    for rel, i in no_valid_blocks:
        print(f"   {rel}:{i}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
