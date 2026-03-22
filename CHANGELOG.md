# Changelog

本文件记录 `Narra` 的版本变更。

维护规则：

- 未发布的改动，先写入 `Unreleased`
- 准备发版时，将对应内容移动到正式版本号下
- 每次版本记录尽量按类型归档：
  - `Added`：新增功能
  - `Changed`：已有功能调整
  - `Fixed`：缺陷修复
  - `Refactored`：重构或内部整理
  - `Docs`：文档与发布说明调整

---

## [Unreleased]

### Added

- 待补充

### Changed

- 待补充

### Fixed

- 待补充

### Refactored

- 待补充

### Docs

- 待补充

---

## [1.0.2] - 2026-03-22

### Added

- 沉浸式扮演支持“重回此回合”
- 沉浸式扮演消息支持长按复制
- 沉浸式扮演输入区接入“特殊玩法 -> 转账”

### Changed

- AI 帮写建议区改为内部滚动展示，长文本不再被压缩截断
- AI 帮写点选建议后会自动写入输入框并收起建议区
- 沉浸式扮演与聊天页复用统一的转账卡片展示逻辑

### Fixed

- 统一 `400` / `429` AI 请求错误提示，补充兼容性与限流说明
- 修正 Gemini OpenAI 兼容地址规范化逻辑
- 修复 Tavern PNG 角色卡导入解析，恢复图片角色卡预览与导入

### Refactored

- 沉浸式扮演消息映射补齐特殊玩法与消息状态信息
- 抽出共享转账卡片组件与转账状态更新逻辑

### Docs

- 更新当前正式版本到 `1.0.2`

---

## [1.0.0] - 2026-03-22

### Added

- 确立正式英文名 `Narra`
- 固定正式 `applicationId` 为 `com.narra.app`
- 建立版本规则：`major.minor.patch`
- 建立渠道规则：`release` / `dev` / `baseline`
- 新增沉浸式角色扮演模块的 `AI帮写 / 剧情建议` MVP

### Changed

- 统一构建产物命名规则为 `Narra-v{versionName}-{versionCode}-{channel}.apk`
- 统一调试包显示名为 `Narra Dev`
- 统一基线包显示名为 `Narra Baseline`

### Docs

- 新增版本策略文档
- 新增发布与更新约定文档
