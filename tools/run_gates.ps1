# 守门：数据引用 / 资源一致性（含打包）/ 产物 jar 完整性 / Ponder 场景注册，可选再跑守门自检
# 用法：powershell -File tools/run_gates.ps1 [-SelfTest]
#
# 为什么要有 jar 完整性这一关：2026-10-11 20:37 的实机崩溃
# （IllegalStateException: Failed to load registries due to above errors）
# 真正原因是 mods 里那份 jar 在 `data/vs_clockwork/{worldgen,neoforge,jukebox_song}`
# 的 5 个条目上抛 `java.util.zip.ZipException: invalid LOC header (bad signature)`：
# 文件字节坏了，Minecraft 连读都读不出来，于是这 5 个注册项全部解析失败。
# 数据的字段/引用此时其实是对的，光看 JSON 永远查不出来，所以必须单独校验 jar 本身。
# 2026-10-11 夜：针对"物品/方块纹理几乎全错"的反馈，check_assets.py 重写为 11 项并加 --jar，
# 详细诊断与被排除的假设见 clockwork_unoffical/docs/ASSET-AUDIT.md。
# 2026-10-17：新增 check_ponder.py —— Ponder 场景的 nbt/方块/lang 三方一致性
# （nbt 缺失会在 Ponder 注册期直接抛异常；见脚本头注释）。
param([switch]$SelfTest)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$env:PYTHONIOENCODING = "utf-8"

$python = "python"
$failed = 0

Write-Host "===== [1/5] tools/check_data_refs.py（数据引用 + worldgen/biome_modifier/jukebox_song 结构）====="
& $python "$root\tools\check_data_refs.py"
if ($LASTEXITCODE -ne 0) { Write-Host "check_data_refs.py 退出码 $LASTEXITCODE"; $failed++ }

Write-Host ""
Write-Host "===== [2/5] tools/check_assets.py --jar（模型/贴图链 + OBJ + 贴图键 + 打包一致性）====="
& $python "$root\tools\check_assets.py" --jar
if ($LASTEXITCODE -ne 0) { Write-Host "check_assets.py 退出码 $LASTEXITCODE"; $failed++ }

Write-Host ""
Write-Host "===== [3/5] tools/check_jar.py（产物 jar 的 zip 结构完整性）====="
$jar = Join-Path $root "clockwork_unoffical\build\libs\vs_clockwork-0.6.0-1.21.1.jar"
if (Test-Path $jar) {
    & $python "$root\tools\check_jar.py" $jar
    if ($LASTEXITCODE -ne 0) { $failed++ }
} else {
    Write-Host "未找到产物 jar（先跑 cd clockwork_unoffical; .\gradlew.bat --offline build）: $jar"
    $failed++
}

Write-Host ""
Write-Host "===== [4/5] tools/check_ponder.py（Ponder 场景 nbt / 方块注册 / lang 键）====="
& $python "$root\tools\check_ponder.py"
if ($LASTEXITCODE -ne 0) { Write-Host "check_ponder.py 退出码 $LASTEXITCODE"; $failed++ }

Write-Host ""
Write-Host "===== [5/5] tools/check_render_coverage.py（渲染覆盖：BER / partial / RenderShape / 物品模型）====="
& $python "$root\tools\check_render_coverage.py"
if ($LASTEXITCODE -ne 0) { Write-Host "check_render_coverage.py 退出码 $LASTEXITCODE"; $failed++ }

if ($SelfTest) {
    Write-Host ""
    Write-Host "===== [附加] 守门脚本自检（故意造坏数据，必须都被报出来）====="
    & $python "$root\tools\selftest_data_refs.py"
    if ($LASTEXITCODE -ne 0) { Write-Host "selftest_data_refs.py 退出码 $LASTEXITCODE"; $failed++ }
    & $python "$root\tools\selftest_assets.py"
    if ($LASTEXITCODE -ne 0) { Write-Host "selftest_assets.py 退出码 $LASTEXITCODE"; $failed++ }
    & $python "$root\tools\check_ponder.py" --selftest
    if ($LASTEXITCODE -ne 0) { Write-Host "check_ponder.py --selftest 退出码 $LASTEXITCODE"; $failed++ }
    # 伸缩杆朝向 / 软管：6 朝向 × 26 连接方向的几何自检（详见脚本头注释与 docs/EXTENDON-ORIENTATION.md）
    & $python "$root\tools\selftest_extendon_orient.py"
    if ($LASTEXITCODE -ne 0) { Write-Host "selftest_extendon_orient.py 退出码 $LASTEXITCODE"; $failed++ }
    # 气体储罐：结构字段只在服务端结构路径改写 / 周期同步不写 blockstate / 物品图标不复用 CT 模型
    & $python "$root\tools\selftest_tank_connectivity.py"
    if ($LASTEXITCODE -ne 0) { Write-Host "selftest_tank_connectivity.py 退出码 $LASTEXITCODE"; $failed++ }
}

Write-Host ""
if ($failed -eq 0) { Write-Host "守门全部通过。" } else { Write-Host "守门失败项：$failed" }
exit $failed
