# 喵啊记账 · 自动记账 App 复刻

> 以 `kmp/` 单基座物理复刻 `com.android.miaoa.achai` v1.8.1 的 21 个功能模块、约 100 个业务页面与 112 个业务接口，App 保持无广告。UI 采用「灯影」设计语言（灯影为外部 UI 基准）。
> 工程宪法见 [AGENTS.md](AGENTS.md)；当前状态见 [功能差距表](docs/功能差距表.md)。

## 这是什么

上一级目录 `D:\workspace\recordAccount\` 是对一个自动记账 Android APK 的完整逆向交付（6 份文档 + jadx/apktool 产物 + REA evidence）。本工程以 `kmp/` 为交付形态：

- **范围**：`01` 的 21 个模块、约 100 个业务页面、`03` 的 112 个业务端点；逐项状态见 [功能差距表](docs/功能差距表.md)
- **架构**：`:app` 装配 Android 系统组件，`:shared` 承载 SQLDelight、契约层与业务规则，`:ui` 承载 Compose 页面
- **数据**：本地优先写入，按 `client_id` 幂等，再由 `sync_queue` 同步
- **UI**：灯影配色、胶囊控件、底部 Dock 与深浅色三态；玻璃质感（`Glass.kt`）与字阶（`AppTypography`）尚未实现，见 [风格对照](docs/风格对照.md)
- **无广告**：不接入广告 SDK，不调用广告请求端点，广告开关固定关闭

完整设计见 [设计文档](docs/superpowers/specs/2026-11-11-kmp-full-replica-design.md)，切片顺序见 [实施计划](docs/superpowers/plans/2026-11-11-kmp-full-replica-plan.md)。

## 工程目录

| 目录 | 用途 |
|---|---|
| `kmp/` | 交付物：Android App、三模块 KMP 源码与测试 |
| `spec/` | 端点、DDL 与功能矩阵 |
| `docs/` | 设计、实施计划、接口对照、页面对照、风格与差距表 |
| `src/` + `public/` | Web 规格参考 |
| `android-shell/` | WebView 规格参考 |

## 运行与构建

本仓库**没有** `gradlew` / `gradlew.bat` 脚本，只有 `kmp/gradle/wrapper/gradle-wrapper.jar` 与 `gradle-wrapper.properties`（Gradle 8.11.1）。因此从 `kmp/` 目录直接用 wrapper 的 main 类运行：

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

两条命令也可一次跑完：`... GradleWrapperMain :shared:jvmTest :app:assembleDebug`。2026-11-12 在 JDK 17.0.21 + Gradle 8.11.1 上实测：`origin/master` 基线 `:shared:jvmTest` 139 tests、0 failures；`:app:assembleDebug` BUILD SUCCESSFUL。

`kmp/.gradle-java.properties`、`kmp/local.properties` 和 `kmp/app/src/androidTest/` 只存本机配置，不进仓库。

Web 规格参考用以下命令构建（不是交付物验收）：

```bash
npm install
npm run build
```

## 版本号

`kmp/app/build.gradle.kts` 里交付物自身的 `versionName = "1.8.8"`、`versionCode = 8`；而复刻**行为目标**是样本 `com.android.miaoa.achai` v1.8.1。仓库里没有文档解释这个差异，此处如实登记：1.8.8 是本工程 App 的版本号，1.8.1 是被复刻样本的版本号，两者不是同一个口径。

## 里程碑

下表按 [实施计划](docs/superpowers/plans/2026-11-11-kmp-full-replica-plan.md) 的 S0–S8 与 `git log` 提交记录整理。「状态」为对应切片的代码/文档落地情况；未落地项见 [功能差距表 §1 与「剩余缺口」](docs/功能差距表.md)。

| 切片 | 内容 | 状态 | 依据提交 |
|---|---|---|---|
| S0 | 全量依据、差距表、工程约定与验收口径 | 完成（差距表与页面对照仍在持续对账） | `37b73ee`、`72f6004`、`b756323`、`ce0cbb2` |
| S1 | 应用框架、Android 组件与动态权限 | 完成（真机验证待补） | `76f82b2`、`ab51295` |
| S2 | 账单、分类标签、账本协作与本地优先同步 | 完成（真机装机待用户配合） | `bfef0ee`、`e3a4b25`、`07a053d` |
| S3 | 预算、统计报表与下钻 | 完成（部分：复制年预算、自定义区间未做） | `ad7a125` |
| S4 | 自动记账采集、OCR、导入导出 | 完成（部分：截屏 MediaProjection 未实现、真机验证待配合） | `bd70802`、`1dbf13b` |
| S5 | 资产、存钱、周期、分期与桌面小组件 | 完成（部分：账户间调拨独立页、7 个配置 Activity 未做） | `09558c5`、`d93feec` |
| S6 | 账号安全、VIP 与支付回调契约 | 完成（契约与本地页；真实支付网关需资质） | `8e921db` |
| S7 | 推送保活、埋点、远程配置与反馈契约 | 完成（100+ 事件映射与厂商通道待服务端） | `8e921db` |
| S8 | 无广检查与全量回归 | 完成（部分：广告依赖黑名单脚本、Android 仪器/真机测试未落地） | `2a8c806`、`6aff48e` |

后续增量提交：`b2b1d42` 复制上月预算与资产流水页、`b27724f` 愿望清单/类型下钻/退款编辑/小组件配置、`eb9de0b` 记一笔附图选传、`655e2d0` CI webhook。`S0–S8` 的「完成」指切片计划里的代码与契约已落地，不等于 [AGENTS.md §11](AGENTS.md#11-definition-of-done-每个里程碑) 的全部 DoD 门禁都通过；未过门禁项见差距表「剩余缺口」。

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
