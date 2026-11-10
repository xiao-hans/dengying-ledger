# 灯影记账 · 自动记账 App 复刻

> 对齐 `com.android.miaoa.achai` v1.8.1 的全量功能面与 112 个业务接口，UI 采用「灯影」设计语言。
> 工程宪法见 [AGENTS.md](AGENTS.md)。

## 这是什么

上一级目录 `D:\workspace\recordAccount\` 是对一个自动记账 Android APK 的完整逆向交付（6 份文档 + jadx/apktool 产物 + REA evidence）。本仓库按 `04-复刻实现蓝图.md` 从零复刻：

- **功能**：21 个模块逐项覆盖（见 [spec/features.md](spec/features.md)）
- **接口**：`spec/endpoints.json` 是从 `jadx_out/sources/com/android/miaoa/achai/api/a.java` 机器提取的权威清单（112 个 `@POST`/`@GET` + 2 个动态 `@Url`），全部在 `src/api/mock/` 有同路径、同字段名的实现
- **数据**：IndexedDB 本地优先，表结构对齐 `spec/ddl.sql`（Room 20+ 表，`client_id` 幂等）
- **UI**：`src/theme/` 直接移植灯影 `Color.kt` / `Glass.kt` / `Theme.kt` 的 token；胶囊控件、玻璃卡、底部 Dock、深浅色三态

## 运行

```bash
npm install
npm run dev        # 本地开发
npm run build      # 生产构建（提交前必跑）
```

## 目录

```
spec/   endpoints.json（接口权威清单）· ddl.sql · features.md（功能矩阵）
docs/   接口实现对照表 · 风格对照
src/theme/   灯影配色 / 玻璃 / 字阶 token
src/db/      IndexedDB DAO（20+ 表）
src/api/     client（统一 fetch 契约）+ mock（112 端点）
src/features/ 21 个业务模块
src/components/ 玻璃卡 / 胶囊 chip / Dock / 对话框
src/app/     导航与根布局
```

## 里程碑

| # | 内容 | 状态 |
|---|---|---|
| M0 | AGENTS.md / CLAUDE.md / spec / git + public 远端 | ✅ |
| M1 | 工程骨架 + 灯影主题 token + 玻璃组件 + Dock 导航 | ✅ |
| M2 | IndexedDB 全表 DAO + 统一 API 契约层 | ✅ |
| M3 | 112 端点 mock 后端 + 接口实现对照表 | ✅ |
| M4 | 账单（记一笔/列表/详情/编辑/删除/搜索）+ 统计（分类报表/趋势/预算/导出） | ✅ |
| M5 | 自动记账三路采集 + 规则引擎 + 去重 + 确认队列 | ✅ |
| M6 | 分类标签 / 账本 / 资产 / 存钱 / 周期 / 分期 / 导入 / 小组件 | ✅ |
| M7 | 账号 / VIP / 订单 / 运营 / 配置 / 设置 / 商业化 no-op | ⏳ |
| M8 | 全量核对（功能矩阵 + 接口对照表）+ 构建验证 + 推送 | ⏳ |

## 证据与限制

- 接口数量以 `api/a.java` 为准（112 注解路径 + 2 动态 `@Url`）；`03-接口文档.md` 写「约 130」含第三方 SDK URL，二者口径不同，见 `docs/接口实现对照表.md`。
- 不复现 `DataNative` 私有加密与密钥，按 `04` §5 明文 JSON 契约等价实现。
- 广告/推送/埋点 SDK 按 `04` §7 裁剪为可插拔 no-op，接口与数据模型保留。
- 协议文本、图标、教程素材自撰自绘，不复制原 App 资产。

## 远端

`https://github.com/xiao-hans/dengying-ledger`（public）
