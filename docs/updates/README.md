# Narra GitHub 更新文件说明

## 固定更新地址

应用内更新元数据基地址已经配置为：

```text
https://0401lucky.github.io/Narra/updates
```

应用会按当前渠道自动读取：

- `dev` -> `https://0401lucky.github.io/Narra/updates/dev.json`
- `release` -> `https://0401lucky.github.io/Narra/updates/release.json`
- `baseline` -> `https://0401lucky.github.io/Narra/updates/baseline.json`

## 推荐发布方式

1. 把当前仓库 push 到 `main`
2. 启用 GitHub Pages，让 `docs/` 目录发布成静态站点
3. 每次构建出新 APK 后：
   - 上传 APK 到 GitHub Releases
   - 复制 APK 直链
   - 修改对应渠道的 JSON：
     - `latest_version_name`
     - `latest_version_code`
     - `minimum_supported_version_code`
     - `apk_url`
     - `apk_sha256`
     - `published_at`
     - `release_notes`
   - 再次 push

## debug/dev 测试建议

先装旧版 `debug` 包，再构建一个更高 `patch` 的新版 `debug` 包。

例如：

- 旧版：`1.0.0-dev` / `10000`
- 新版：`1.0.1-dev` / `10001`

对应 `dev.json` 应写成：

```json
{
  "app_id": "com.narra.app.dev",
  "channel": "dev",
  "latest_version_name": "1.0.1-dev",
  "latest_version_code": 10001,
  "minimum_supported_version_code": 10000,
  "apk_url": "https://github.com/0401lucky/Narra/releases/download/v1.0.1-dev/Narra-v1.0.1-dev-10001-dev.apk",
  "apk_sha256": "替换成真实 sha256",
  "published_at": "2026-03-22T12:00:00+08:00",
  "release_notes": [
    "测试应用内更新"
  ]
}
```

## 计算 SHA256

在项目根目录执行：

```powershell
Get-FileHash .\app\build\outputs\apk\debug\Narra-v1.0.1-dev-10001-dev.apk -Algorithm SHA256
```

把输出中的 `Hash` 填到 `apk_sha256`。
