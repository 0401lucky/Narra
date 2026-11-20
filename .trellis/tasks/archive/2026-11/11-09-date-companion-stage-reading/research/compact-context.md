# D4 短上下文恢复（2026-11-18）

此前原生/Codex worker在大上下文下多次TPM限流，项目默认Claude后端本机3425拒绝连接，均未写D4产品代码。当前仅缩短一次注入，不降低实现范围或规范要求；完整16条清单留在implement-full-context-2026-11-18.jsonl。

先读本任务prd/design/implement及backend index。写代码前按其Pre-Development Checklist完整读实际相关的ui-state/ui-token/roleplay-theme/database/error/companion-foundation，以及共享cross-layer/code-reuse指南；presentation规范与D3最新交接已注入。历史entry-ui/settings-protocol/voice-assets研究按需要查相应接口，不重读全部旧会话或无关版本。

D4任务in_progress，完整v2.4.1范围已批准，D3当前37JVM+37设备、编译/Lint/双APK通过，旧视觉差异未接纳。实现本地Player/Stage、角色素材/CAS/回退、跨场keyset/锚点及真实UI，不降低为文字MVP。源文件所有权/唯一Gradle/12UI保护/无付费模型TTS/不改版本JSON与发布边界见cli-implement-prompt.md。
