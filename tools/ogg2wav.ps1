# 用 Windows 自带的 Media Foundation（WinRT MediaTranscoder）把 ogg 转成 wav，
# 好在没有 ffmpeg 的机器上做包络/频谱事实核对（听不出声，就用数字看"咔哒 vs 气流"）。
# 用法：pwsh -File tools/ogg2wav.ps1 -In <ogg文件...|目录> -OutDir <目录>
param(
    [Parameter(Mandatory = $true)][string[]]$In,
    [Parameter(Mandatory = $true)][string]$OutDir
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null
[Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.StorageFolder, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Transcoding.MediaTranscoder, Windows.Media.Transcoding, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.MediaProperties.MediaEncodingProfile, Windows.Media.MediaProperties, ContentType = WindowsRuntime] | Out-Null

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
$asTaskAction = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction' })[0]

function Await($op, $type) {
    $m = $asTaskGeneric.MakeGenericMethod($type)
    $t = $m.Invoke($null, @($op))
    $t.Wait(-1) | Out-Null
    return $t.Result
}
function AwaitAction($act) {
    $t = $asTaskAction.Invoke($null, @($act))
    $t.Wait(-1) | Out-Null
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$files = @()
foreach ($p in $In) {
    if (Test-Path -PathType Container $p) { $files += (Get-ChildItem $p -Filter *.ogg -File).FullName }
    else { $files += (Resolve-Path $p).Path }
}
$dstFolder = Await ([Windows.Storage.StorageFolder]::GetFolderFromPathAsync((Resolve-Path $OutDir).Path)) ([Windows.Storage.StorageFolder])
$profile = [Windows.Media.MediaProperties.MediaEncodingProfile]::CreateWav([Windows.Media.MediaProperties.AudioEncodingQuality]::High)
$ok = 0
foreach ($f in $files) {
    $name = [System.IO.Path]::GetFileNameWithoutExtension($f) + ".wav"
    try {
        $src = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($f)) ([Windows.Storage.StorageFile])
        $out = Await ($dstFolder.CreateFileAsync($name, [Windows.Storage.CreationCollisionOption]::ReplaceExisting)) ([Windows.Storage.StorageFile])
        $transcoder = New-Object Windows.Media.Transcoding.MediaTranscoder
        $prep = Await ($transcoder.PrepareFileTranscodeAsync($src, $out, $profile)) ([Windows.Media.Transcoding.PrepareTranscodeResult])
        if (-not $prep.CanTranscode) { Write-Host ("FAIL  " + $name + "  " + $prep.FailureReason); continue }
        AwaitAction ($prep.TranscodeAsync())
        $ok++
        Write-Host ("ok    " + $name)
    } catch {
        Write-Host ("ERR   " + $name + "  " + $_.Exception.Message)
    }
}
Write-Host ("转换完成 " + $ok + "/" + $files.Count)
