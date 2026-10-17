# Clockwork 装包：临时文件 → sha256 校验 → 同目录 Move-Item（原子替换）
# 前置：游戏必须没在运行（本脚本自己也会再确认一次）
$ErrorActionPreference = "Stop"
$root = "C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork"
$src  = Join-Path $root "clockwork_unoffical\build\libs\vs_clockwork-0.6.0-1.21.1.jar"
$backupDir = Join-Path $root "_cw_backups"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

$targets = @(
  "C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods",
  "$env:APPDATA\PrismLauncher\instances\CSN_The Song Of The Sky\minecraft\mods",
  "C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\CSN_The Song Of The Sky\mods"
)

# --- 前置 1：没有 javaw（Minecraft 主进程）在跑 ---
$mc = Get-Process javaw -ErrorAction SilentlyContinue
if ($mc) { Write-Host "中止：仍有 javaw 进程（Minecraft 可能在运行）：$($mc.Id -join ',')"; exit 2 }

# --- 前置 2：latest.log 距今 > 2 分钟 ---
$log = "C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\logs\latest.log"
if (Test-Path $log) {
  $age = ((Get-Date) - (Get-Item $log).LastWriteTime).TotalMinutes
  Write-Host ("latest.log 距今 {0:N1} 分钟" -f $age)
  if ($age -lt 2) { Write-Host "中止：latest.log 太新（<2 分钟），游戏可能正在运行"; exit 2 }
}

$srcHash = (Get-FileHash $src -Algorithm SHA256).Hash
Write-Host "源: $src"
Write-Host "源 SHA256: $srcHash"
Write-Host ("源大小: {0} 字节`n" -f (Get-Item $src).Length)

New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$ok = 0
foreach ($dir in $targets) {
  if (-not (Test-Path $dir)) { Write-Host "[跳过] 目录不存在: $dir"; continue }
  $dst = Join-Path $dir "vs_clockwork-0.6.0-1.21.1.jar"
  Write-Host "== 目标: $dst"

  # 备份旧文件（若存在且哈希不同）
  if (Test-Path $dst) {
    $oldHash = (Get-FileHash $dst -Algorithm SHA256).Hash
    if ($oldHash -eq $srcHash) { Write-Host "   已是同一份（哈希一致），仍然覆盖一次" }
    $tag = Split-Path $dir -Parent | Split-Path -Leaf
    Copy-Item $dst (Join-Path $backupDir "$tag-vs_clockwork-0.6.0-1.21.1.jar.pretexturefix-$stamp.bak") -Force
  }

  # 临时文件 → 校验 → 同目录原子替换
  $tmp = Join-Path $dir "vs_clockwork-0.6.0-1.21.1.jar.tmp-$stamp"
  Copy-Item $src $tmp -Force
  $tmpHash = (Get-FileHash $tmp -Algorithm SHA256).Hash
  if ($tmpHash -ne $srcHash) { Remove-Item $tmp -Force; throw "临时文件哈希不一致: $tmp" }
  # PS 5.1 的 Move-Item -Force 不会覆盖已存在的文件（.NET File.Move 没有 overwrite 参数），
  # 目标存在时抛 "Cannot create a file when that file already exists"，并在 mods 里留下 .tmp-* 残留。
  # 先用 File.Replace 做真正的原子替换；失败（目标不存在/跨卷）再退回删目标 + 移动，之后仍做 sha256 校验。
  try {
    [System.IO.File]::Replace($tmp, $dst, $null)
  } catch {
    if (Test-Path $dst) { Remove-Item $dst -Force }
    Move-Item $tmp $dst
  }
  $newHash = (Get-FileHash $dst -Algorithm SHA256).Hash
  if ($newHash -ne $srcHash) { throw "安装后哈希不一致: $dst" }
  Write-Host "   已安装，SHA256 校验通过 ($newHash)"
  $ok++
}
Write-Host "`n完成：成功安装 $ok 个位置（时间戳 $stamp）"
