# Narra

Narra 是一个基于 Jetpack Compose 的 Android AI 对话应用，当前包含聊天、角色扮演、世界书、记忆、上下文组装、屏幕翻译、应用内更新与 baseline profile 等能力。

当前 Provider 文本接口已支持：
- OpenAI Compatible `/chat/completions`
- OpenAI Compatible `/responses`（第一阶段接入）
- Anthropic `/v1/messages`

同时支持：
- 自定义 `chatCompletionsPath`
- 按 Provider 选择文本接口模式
- 基于协议区分的模型拉取与调用链路

## 本地开发

常用命令：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat app:assembleBaseline
.\gradlew.bat app:assembleDebugAndroidTest
```

## 项目结构

- `app`：主应用模块
- `baselineprofile`：baseline profile 与启动基准测试
- `docs`：版本、发版与更新约定
- `app/schemas`：Room schema 导出目录

## 当前质量门禁

CI 默认执行以下检查：

- `assembleDebug`
- `testDebugUnitTest`
- `lintDebug`

## 说明

- `dev` 是默认分发与内置更新测试渠道
- `baseline` 构建用于 profile 与性能链路验证
- 根目录下的 `tmp/`、`tmp-*`、日志等文件视为临时产物，不应提交
