# 喵啊记账 · 自动记账 App 复刻

> 以 `kmp/` 单基座物理复刻 `com.android.miaoa.achai` v1.8.1 的 21 个功能模块、约 100 个业务页面与 112 个业务接口，App 保持无广告。UI 采用「灯影」设计语言（灯影为外部 UI 基准）。
> 工程宪法见 [AGENTS.md](AGENTS.md)；当前状态见 [功能差距表](docs/功能差距表.md)。

## 这是什么

上一级目录 `D:\workspace\recordAccount\` 是对一个自动记账 Android APK 的完整逆向交付（6 份文档 + jadx/apktool 产物 + REA evidence）。本工程以 `kmp/` 为交付形态：

- **范围**：`01` 的 21 个模块、约 100 个业务页面、`03` 的 112 个业务端点；逐项状态见 [功能差距表](docs/功能差距表.md)
- **架构**：`:app` 装配 Android 系统组件，`:shared` 承载 SQLDelight、契约层与业务规则，`:ui` 承载 Compose 页面
- **数据**：本地优先写入，按 `client_id` 幂等，再由 `sync_queue` 同步
- **UI**：灯影配色、玻璃质感、胶囊控件、底部 Dock 与深浅色三态
- **无广告**：不接入广告 SDK，不调用广告请求端点，广告开关固定关闭

## 工程目录

| 目录 | 用途 |
|---|---|
| `kmp/` | 交付物：Android App、三模块 KMP 源码与测试 |
| `spec/` | 端点、DDL 与功能矩阵 |
| `docs/` | 设计、实施计划、接口对照、页面对照、风格与差距表 |
| `src/` + `public/` | Web 规格参考 |
| `android-shell/` | WebView 规格参考 |

完整设计见 [设计文档](docs/superpowers/specs/2026-11-11-kmp-full-replica-design.md)，切片顺序见 [实施计划](docs/superpowers/plans/2026-11-11-kmp-full-replica-plan.md)。

## 运行

构建交付物 `kmp/` 时，在 `kmp/` 目录用 Gradle 8.11.1 和 JDK 17 以上运行 `:app:assembleDebug :shared:jvmTest`。本机没有 `gradlew` 脚本，需指定已安装的 Gradle 与 JBR 路径。`kmp/.gradle-java.properties`、`kmp/local.properties` 和 `kmp/app/src/androidTest/` 只存本机配置，不进仓库。

Web 规格参考用以下命令构建：

```bash
npm install
npm run build
```

## 里程碑

| 切片 | 内容 | 状态 |
|---|---|---|
| S0 | 全量依据、差距表、工程约定与验收口径 | 执行中 |
| S1 | 应用框架、Android 组件与动态权限 | 未开始 |
| S2 | 账单、分类标签、账本协作与本地优先同步 | 未开始 |
| S3 | 预算、统计报表与下钻 | 未开始 |
| S4 | 自动记账三路采集、OCR、导入导出 | 未开始 |
| S5 | 资产、存钱、周期、分期与桌面小组件 | 未开始 |
| S6 | 账号安全、VIP 与支付回调 | 未开始 |
| S7 | 推送保活、埋点、远程配置与反馈 | 未开始 |
| S8 | 无广检查与全量回归 | 未开始 |

每个切片的 Definition of Done 见 [AGENTS.md §11](AGENTS.md#11-definition-of-done-每个里程碑)。

## 证据与限制

- 接口数量以 `api/a.java` 为准（112 注解路径 + 2 动态 `@Url`）；`03-接口文档.md` 写「约 130」含第三方 SDK URL，二者口径不同。
- 样本 `data=` 私有加密与密钥不复现；KMP 契约层发送明文 JSON，保留样本字段名与统一响应封装。
- 6 家广告 ADN 与自有 ADX 不接入；广告请求端点不调用，广告开关固定关闭。
- 推送、埋点、支付与运营商一键登录使用可替换网关；真实商户资质缺失时，测试环境使用沙箱。
- 服务端真实响应与鉴权续期缺少动态抓包证据。开放差异见 [设计文档 §8](docs/superpowers/specs/2026-11-11-kmp-full-replica-design.md)。
- 协议文本、图标、教程素材自撰自绘，不复制原 App 资产。

## 远端

`https://github.com/xiao-hans/meow-ledger`（public）
