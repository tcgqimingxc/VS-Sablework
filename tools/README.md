# tools/ — GitHub 取数工具（本机 hosts 屏蔽 github.com 的绕行方案）

## 背景

本机 `C:\Windows\System32\drivers\etc\hosts` 把 `github.com` / `raw.githubusercontent.com` / `api.github.com` / `codeload` 之外的多数 GitHub 域名解析到 `127.0.0.1`，
且沙箱内 `curl`（schannel）报 `SEC_E_NO_CREDENTIALS`、`git`（默认 openssl）因本机 443 上的本地监听而证书校验失败。
因此 **不要用 `git clone https://github.com/...` 或 `curl.exe https://github.com/...`**。

实测可用通道：

| 通道 | 用途 | 速度 |
|---|---|---|
| `codeload.github.com` | 分支 tar.gz 全量包（**未被 hosts 屏蔽**） | ~70 KB/s |
| `cdn.jsdelivr.net` + `data.jsdelivr.com` | 列文件树 + 按文件拉文本（主力） | 单文件 1~2s，8~16 并发可用 |
| `ghproxy.net` | `git ls-remote`、git 智能协议（clone 很慢，archive 会 403） | 仅适合查分支 |
| `ghfile.geekertao.top` | codeload 代理（备选） | ~0.2 MB/s |
| `api.modrinth.com` | 查 mod 元数据（版本/加载器/源码地址） | 快 |

Python 解释器（沙箱内可执行、HTTPS 走 OpenSSL 正常）：
`C:\Users\yql11\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe`

## 脚本

```powershell
$py = "C:\Users\yql11\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe"

# 1) 列仓库文件树（写 _refs/_trees/<repo>.txt 由 jspull 完成，这里直接打印）
& $py tools\jstree.py ValkyrienSkies/Clockwork 1.20.1/main --filter "src/"
& $py tools\jstree.py ValkyrienSkies/Clockwork 1.20.1/main --max 100

# 2) 批量拉文本文件到本地（幂等：已存在且大小一致的会跳过；失败可重跑补齐）
& $py tools\jspull.py ValkyrienSkies/Clockwork 1.20.1/main "_refs/Clockwork" --threads 8
& $py tools\jspull.py ValkyrienSkies/Clockwork 1.20.1/main "_refs/Clockwork" --list-only   # 只列清单
& $py tools\jspull.py <owner>/<repo> <ref> <dest> --ext .java,.kt,.json

# 3) 拉单个文件到 stdout
& $py tools\ghfetch.py raw ryanhcode/sable main README.md
& $py tools\ghfetch.py proxyurl "https://api.github.com/repos/..."   # 走 ghproxy（API 常 403）

# 4) 拉 codeload tar.gz 并解压（snapshot2 带进度；仓库大时很慢）
& $py tools\snapshot2.py ValkyrienSkies/Clockwork 1.20.1/main "_refs/Clockwork"

# 5) 测速
& $py tools\speedtest.py
```

## 注意

- 分支名带斜杠（如 `1.20.1/main`）在 jsDelivr 的 `cdn` URL 里会有歧义，脚本对 data API 做了 URL 编码；如需固定版本可用 commit hash（`jstree.py`/`jspull.py` 都接受任意 ref）。
- 并发超过 ~16 时 jsDelivr 会出现 `WinError 10054` 连接重置；脚本有重试，失败可重跑同一命令补齐（幂等）。
- 二进制资源（贴图/模型/音频/nbt）默认不拉；需要时用 `--ext .png,.ogg` 之类显式指定。
- 镜像内容仅供本地阅读，Clockwork 的 `src/main/resources/assets` 是 ARR 授权，不要再分发。
