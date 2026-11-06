# 首轮捕获未冻结

本目录完整保留 63 张 actual 及 64 项 missing-original/计数守卫失败，但不是可交付 before。

- 四张 `editor-book-popup` / `editor-conversation-popup` 原生 PixelCopy 实际取到 Activity 裁片；独立语义 root 正确仍不足以证明像素 owner。人工查看探针图时发现，不作为有效窗口截图。
- 后续测试改用真实 Popup AndroidComposeView 的 native Canvas，并验证 owner 与 Activity 不同。独立蓝底页/品红 Popup 像素哨兵验证拒绝底页像素；真实两类 Popup 再目视核验。
- 原书名 fixture 的 5 个选项可全部容纳，实际滚动为 0/0；在产品未变时增加为 8 个合理长书名，保证末项场景有真实正向滚动范围，再统一重取。
- 正文探针的 Stage FocusManager 未能清除 Sheet owner 焦点。最终宿主改为正文真实 ScrollBy + Sheet owner 清焦点，并显式断言 `Focused=false` 与内部 offset=maximum、末行完全进入视口。
- 所有调整只发生在测试设施与合成 fixture；未修改产品，旧 158 张 PNG 保持。最终 before 必须来自同一套修正后的捕获源码和完整新一轮 verify。
