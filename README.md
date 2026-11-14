# 喵啊记账

**自动记账 App：账单自动入账、预算统计、资产存钱、多账本协作。本地优先，无广告。**

An auto bookkeeping app — automatic bill capture, budgets & reports, assets & savings,
multi-ledger sharing. Local-first, no ads.

## 功能 Features

- **自动记账**：短信与账单自动识别入账，支持 OCR 识别票据、导入导出
- **记一笔**：快速记账，支持附图；分类标签、账本协作，数据本地优先写入
- **预算与统计**：预算管理、统计报表与下钻，复制上月预算、自定义区间
- **资产与存钱**：资产流水、存钱计划、周期记账、分期、愿望清单、桌面小组件
- **多账本协作**：多账本共享，账单、分类与协作数据按 `client_id` 幂等同步
- **本地优先**：先写本地，再由 `sync_queue` 同步；断网也能记账
- **无广告**：不接入广告 SDK，不调用广告请求，广告开关固定关闭

## 技术架构

Kotlin Multiplatform 三模块结构：

| 模块 | 职责 |
|---|---|
| `:app` | Android 装配：系统组件、权限、推送保活 |
| `:shared` | SQLDelight、契约层与业务规则、同步队列 |
| `:ui` | Compose 页面：灯影配色、胶囊控件、底部 Dock，深浅色三态 |

UI 采用灯影设计语言。工程约定见 [AGENTS.md](AGENTS.md)。

## 工程目录

| 目录 | 用途 |
|---|---|
| `kmp/` | 交付物：Android App、三模块 KMP 源码与测试 |
| `spec/` | 端点、DDL 与功能矩阵 |
| `docs/` | 设计、实施计划、接口与页面对照、风格 |
| `src/` + `public/` | Web 规格参考 |
| `android-shell/` | WebView 规格参考 |

## 运行与构建

本仓库**没有** `gradlew` / `gradlew.bat` 脚本，只有 `kmp/gradle/wrapper/gradle-wrapper.jar` 与
`gradle-wrapper.properties`（Gradle 8.11.1）。因此从 `kmp/` 目录直接用 wrapper 的 main 类运行：

```bash
cd kmp
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :shared:jvmTest
```

前提：

- JDK 17（`JAVA_HOME` 指向 JDK 17）。
- 已安装 Android SDK，并在 `kmp/local.properties` 写 `sdk.dir=<绝对路径>`；该文件被 `.gitignore` 忽略，不进仓库。
- 首次运行 wrapper jar 会按 `gradle-wrapper.properties` 的 `distributionUrl` 下载 Gradle 8.11.1 发行包，需要本地缓存或网络可达。

`:app:assembleDebug` 构建 Android 调试包：

```bash
cd kmp
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :app:assembleDebug
```

两条命令也可一次跑完：`... GradleWrapperMain :shared:jvmTest :app:assembleDebug`。
2026-11-12 在 JDK 17.0.21 + Gradle 8.11.1 上实测：`:shared:jvmTest` 139 tests、0 failures；
`:app:assembleDebug` BUILD SUCCESSFUL。

`kmp/.gradle-java.properties`、`kmp/local.properties` 和 `kmp/app/src/androidTest/` 只存本机配置，不进仓库。

Web 规格参考用以下命令构建（不是交付物验收）：

```bash
npm install
npm run build
```

## 版本号

`kmp/app/build.gradle.kts` 里 `versionName = "1.8.8"`、`versionCode = 8`。

## 路线图

- ~~玻璃质感（`Glass.kt`）与字阶（`AppTypography`）落地~~ 已落地并构建验证；模糊观感与三态视觉待真机验证
- 截屏自动记账（MediaProjection）采集
- 真机验证：装机、同步、推送与厂商通道
- 复制年预算、自定义区间、账户间调拨独立页等剩余页面补齐

问题与建议请提 Issue。
