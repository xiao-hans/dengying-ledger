# 喵啊记账 · 自动记账 App 复刻

> 对齐 `com.android.miaoa.achai` v1.8.1 的全量功能面与 112 个业务接口，UI 采用「灯影」设计语言的配色与玻璃质感（灯影为外部 UI 基准）。
> 工程宪法见 [AGENTS.md](AGENTS.md)。

## 这是什么

上一级目录 `D:\workspace\recordAccount\` 是对一个自动记账 Android APK 的完整逆向交付（6 份文档 + jadx/apktool 产物 + REA evidence）。本仓库按 `04-复刻实现蓝图.md` 从零复刻：

- **功能**：21 个模块逐项覆盖（见 [spec/features.md](spec/features.md)）
- **接口**：`spec/endpoints.json` 是从 `jadx_out/sources/com/android/miaoa/achai/api/a.java` 机器提取的权威清单（112 个 `@POST`/`@GET` + 2 个动态 `@Url`），全部在 `src/api/mock/` 有同路径、同字段名的实现
- **数据**：IndexedDB 本地优先，表结构对齐 `spec/ddl.sql`（Room 20+ 表，`client_id` 幂等）
- **UI**：`src/theme/` 直接移植外部 UI 基准灯影的 `Color.kt` / `Glass.kt` / `Theme.kt` 的 token；胶囊控件、玻璃卡、底部 Dock、深浅色三态

## 三套实现

本仓库同时保留三套实现。Web 版实现全量 112 个业务端点；两套 Android 形态不实现网络接口。

| 目录 | 形态 | 数据层 | 接口 |
|---|---|---|---|
| `src/` + `public/` | Vite + React SPA | IndexedDB | `src/api/mock/` 实现 112 个端点 |
| `android-shell/` | Java WebView 壳，加载 `dist/` 产物 | 复用 Web 版 IndexedDB | 复用 Web 版 mock 后端 |
| `kmp/` | Kotlin Multiplatform + Compose 原生页面 | SQLDelight 本地库 | 无网络层，账号、订单、推送均为本地模拟 |

`android-shell/` 的包名是 `com.meow.ledger`，版本是 `1.8.7`。它把 `npm run build` 的产物复制到 `android-shell/app/src/main/assets/`，再从 `https://appassets.androidplatform.net/index.html` 加载，使 IndexedDB 在安全上下文中可用。

`kmp/` 的版本是 `1.8.8`，包含 `:app`、`:shared`、`:ui` 三个模块。它用本地数据库覆盖账单、统计、自动记账和「我的」各页面，不访问服务端。`kmp/` 的页面数量少于 Web 版，也不替代 `src/api/mock/` 的端点实现。

## 运行

```bash
npm install
npm run dev        # 本地开发
npm run build      # 生产构建（提交前必跑）
```

构建 `android-shell/` 时，先执行 `npm run build`，再复制 `dist/` 到 `android-shell/app/src/main/assets/`。

构建 `kmp/` 时，在 `kmp/` 目录用 Gradle 8.11.1 和 JDK 17 以上运行 `:app:assembleDebug :shared:jvmTest`。本机没有 `gradlew` 脚本，需指定已安装的 Gradle 与 JBR 路径。`kmp/.gradle-java.properties`、`kmp/local.properties` 和 `kmp/app/src/androidTest/` 只存本机配置，不进仓库。

## 目录

```
spec/          endpoints.json（接口权威清单）· ddl.sql · features.md（功能矩阵）
docs/          接口实现对照表 · 风格对照
src/theme/     灯影配色（外部 UI 基准） / 玻璃 / 字阶 token
src/db/        IndexedDB DAO（20+ 表）
src/api/       client（统一 fetch 契约）+ mock（112 端点）
src/features/  21 个业务模块
src/components/ 玻璃卡 / 胶囊 chip / Dock / 对话框
src/app/       导航与根布局
android-shell/  WebView 壳，加载 dist/ 产物
kmp/           Kotlin Multiplatform + Compose 页面与 SQLDelight 本地库
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
| M7 | 账号 / VIP / 订单 / 运营 / 配置 / 设置 / 商业化 no-op | ✅ |
| M8 | 全量核对（功能矩阵 + 接口对照表）+ 构建验证 + 推送 | ✅ |
| M9 | WebView 壳与 KMP 本地页面、数据库迁移、测试 | ✅（本地功能） |

## 证据与限制

- 接口数量以 `api/a.java` 为准（112 注解路径 + 2 动态 `@Url`）；`03-接口文档.md` 写「约 130」含第三方 SDK URL，二者口径不同，见 `docs/接口实现对照表.md`。
- KMP 版不实现网络接口。112 端点实现只在 Web 版 `src/api/mock/`；`android-shell/` 复用 Web 版产物。边界见 `docs/接口实现对照表.md` 的「实现边界」。
- 不复现 `DataNative` 私有加密与密钥，按 `04` §5 明文 JSON 契约等价实现。
- 广告/推送/埋点 SDK 按 `04` §7 裁剪为可插拔 no-op，接口与数据模型保留。
- 协议文本、图标、教程素材自撰自绘，不复制原 App 资产。

## 远端

`https://github.com/xiao-hans/meow-ledger`（public）
