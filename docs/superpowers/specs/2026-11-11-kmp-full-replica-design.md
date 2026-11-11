---
title: 喵啊记账 KMP 全量复刻设计
date: 2026-11-11
status: 已批准
approval: 用户在 2026-11-11 预先批准方案 A，并授权不再逐节询问
scope: kmp 单基座、物理全复刻、保持无广告
---

# 喵啊记账 KMP 全量复刻设计

本文档规定 `kmp/` 的全量复刻范围、架构和验收方法。`kmp/` 是唯一交付物。Web 版和 `android-shell/` 只作规格参考，不参与验收。

权威证据按以下顺序使用：`D:\workspace\recordAccount\00-总览与结论.md`、`01-功能点全量清单.md`、`02-业务架构.md`、`03-接口文档.md`、`04-复刻实现蓝图.md`、`05-证据与差异核对.md`。本仓库的 `spec/endpoints.json`、`spec/ddl.sql`、`spec/features.md` 与上述文档冲突时，以上述文档为准，并修正本仓库文档。

## 1. 目标与边界

用户要求复刻全部功能与全部接口。验收范围包含 `01` 的 21 个模块和约 100 个业务自有页面。验收范围还包含 `03` 的 112 个契约端点、`02` 的数据模型和 `04` 的 6 个里程碑。每个页面还应通过 `AGENTS.md` §6 的 10 条交互纪律。

App 应保持无广告。实现不得引入 6 家广告 ADN 或自有 ADX SDK，不得展示广告位。实现保留 `ad_*` 数据模型和 `AdSlotProvider` 空实现；`AdSlotProvider` 恒返回空。实现不调用 `03` §1 和 §4 的广告请求端点。`Config/GetV2` 仍返回 24 个开关；广告开关应固定为关闭。此要求取代 `04` §7 中「可插拔广告位」的旧取舍。

实现应提供通知监听、无障碍读取、MediaProjection 截屏和 OCR。实现还应提供动态权限、精确闹钟、桌面小组件、前台服务、扫码和生物识别。文件导入导出和支付回调也应接入 Android。实现不得用模拟采集代替这些能力；模拟数据只用于自动化测试。

`DataNative.encrypt`、`DataNative.decrypt` 和 `getAutoBillKey` 属样本私有算法。本复刻不透传 `data=` 密文。契约层应发送明文 JSON，并保留样本字段名与统一响应封装。此差异应写入接口对照表和 README。

## 2. 架构

实现保留 `:app`、`:shared`、`:ui` 三个 Gradle 模块。依赖方向为 `:ui` → `:shared`，`:app` 装配两者。`:shared` 不得引用 `:ui` 或 Android `Activity`。

| 单元 | 职责 | 对外接口 |
| --- | --- | --- |
| `shared/db/` | SQLDelight 表、迁移、DAO 和 Repository | `MeowDatabase`、各 Repository |
| `shared/network/` | 112 个契约端点、响应信封、鉴权头和同步队列 | `MeowApi`、分组 API、`SyncEngine` |
| `shared/domain/` | 金额整数分、去重键、`client_id` 幂等键和业务规则 | 纯 Kotlin 函数与值对象 |
| `shared/platform/` | 系统能力的 `expect` 接口 | `CaptureSource`、`AppWidgetBridge`、`AlarmBridge`、`AuthBridge` |
| `app/androidMain/` | 上述接口的 `actual` 实现、Service、Receiver、Provider | Android 组件与绑定对象 |
| `ui/commonMain/` | 约 100 个 Compose 页、导航图和灯影主题 | `MeowApp`、路由和页面组件 |

`shared/network/` 应使用 Ktor Client。`commonMain` 依赖 `io.ktor:ktor-client-core`，`androidMain` 依赖 `io.ktor:ktor-client-okhttp`。Context7 在 2026-11-11 给出的当前稳定版为 `3.6.0`。写入 `kmp/gradle/libs.versions.toml` 前，构建负责人应核对 Ktor `3.6.0` 与 Kotlin `2.1.10` 的兼容性；若二者不兼容，应同时升级 Kotlin 和 Compose Multiplatform，并提交兼容性验证记录。

构建脚本应设置广告依赖黑名单检查。检查发现广告 ADN 或 ADX 依赖时，构建应失败。

## 3. 数据模型与数据流

### 3.1 数据模型

`bill` 使用 `member_id + client_id` 复合主键。`client_id` 是端侧幂等键。`amount_cents` 使用整数分。`day` 使用 `yyyyMMdd` 整数。时间字段使用 `yyyy-MM-dd HH:mm:ss`。`bill.type` 取值为 `1 支出`、`2 收入`、`3 转账`。`bill.source` 取值为 `0 手工`、`1 通知`、`2 截屏`、`3 OCR`、`4 导入`、`5 周期`。

`MeowDatabase` 当前有 28 张表。实现应按 `02` §5、`04` §3 和 `spec/ddl.sql` 补齐 `cate_extend`、`bill_tag`、`bill_remark`、`widget_base`、`widget_desktop`、`widget_hot`、`ad_data`、`ad_info`、`ad_config`、`ad_event`、`ad_click_event`、`ad_trackurl` 等表。新增表应写入新的 `.sqm` 迁移文件。禁止只修改 `.sq` 而漏掉已发布数据库的迁移。

### 3.2 写路径

1. 页面调用 Repository 写入本地库。
2. Repository 生成 `client_id`，并把变更写入 `sync_queue`。
3. 页面读取本地结果并刷新。
4. `SyncEngine` 按队列顺序调用契约端点。
5. 契约端点返回 `code=0` 时，`SyncEngine` 标记记录为已同步。
6. 契约端点失败时，`SyncEngine` 保留队列项并按退避策略重试。

云端不可达时，页面不得阻塞。页面应显示本地结果和同步状态。删除操作应先弹二次确认，再写本地库。

### 3.3 自动记账路径

通知监听、无障碍读取和 MediaProjection 截屏都应把原始事件交给 `shared/domain/` 的解析器。三条路径共用去重键 `hash(sourceApp + money + occurredAt/60s + merchant)`。解析结果应进入待确认队列。用户确认或编辑后，Repository 才写入 `bill`。实现不得静默入账。

识别规则应使用 `rule` 表和 `KeyWordMatchingRule` 契约端点。规则变更应记录到 `behavior_event`。周期提醒应使用精确闹钟，并在开机广播后恢复闹钟。

### 3.4 契约层

`MeowApi` 应覆盖 `spec/endpoints.json` 的 112 个注解路径。这 112 个路径来自 `api/a.java`；`03` §1 和 §4 的广告请求端点不在其中。`Bills/ImportBills` 的两个重载应按请求体字段区分。`Config/GetV2` 应使用 `meow-config.aiyouaiyou.cn`。文件下载和 OSS 直传应使用动态 `@Url` 对应方法。

请求头应包含 `Authorization`、`X-Device-Id`、`X-App-Version` 和 `X-Client-Time`。服务端无动态抓包证据，因此实现应使用可替换的基地址配置；测试环境应指向本地 mock 服务。契约层应解析统一响应 `{code, message, data, trace_id}`，并把 `trace_id` 写入错误日志。

## 4. 错误处理与交互

错误应分为网络错误、契约错误、本地数据库错误和权限拒绝。页面应显示加载中、失败和成功三态。失败态应提供重试按钮。权限拒绝应提供说明页和再次申请入口。

所有表单在页面切换后应保留草稿。提交成功后，表单才清空。金额输入应使用受控字符串，不得吞字符或强制两位小数。空态应提供下一步按钮。筛选条件应显式显示当前值；切换账本不得清空月份和分类。删除、解绑、退出账本和清空数据应有二次确认。深浅色切换应立即生效并持久化。

## 5. 模块切片

方案 A 按模块纵向切片。每个切片应一次做齐表、契约、Android 能力、Compose 页、测试、文档、真机验证和提交推送。

| 切片 | 模块 | 主要产物 | 完成依据 |
| --- | --- | --- | --- |
| S0 | 全量依据 | 差距表、接口对照表、功能矩阵、页面清单 | 21 模块、112 端点、约 100 页各有实现位置或未完成标记 |
| S1 | M1、M20 | Manifest、权限申请、启动链、WebView、扫码、票据、快捷磁贴、悬浮宠物 | 真机可完成权限授予、拒绝回退和入口跳转 |
| S2 | M4、M5、M6 | 账单 CRUD、退款、批量改、搜索、分类标签、账本协作 | 本地写入、幂等同步、二次确认和筛选验证通过 |
| S3 | M7、M8 | 总预算、分类预算、统计报表和下钻 | 金额整数分、包含排除规则、SVG 图表验证通过 |
| S4 | M2、M3、M13 | 三路采集、去重、待确认队列、OCR、导入导出 | 20 条模拟通知识别率 ≥ 90%、重复数 0；10 张支付截图金额和商户正确 |
| S5 | M9、M10、M11、M12、M14 | 资产、存钱、周期、分期、小组件 | 余额联动、期次生成、精确闹钟和桌面小组件验证通过 |
| S6 | M15、M16 | 登录、换绑、应用锁、协议、VIP 和支付回调 | 鉴权、订单状态回查、生物识别和支付回调验证通过 |
| S7 | M18、M19 | 推送通道、保活、埋点、远程配置、反馈、错误日志 | 事件名与 `01` 一致；上报、配置页和反馈链路验证通过 |
| S8 | M17、M21 | 无广静态检查、全量回归、文档收口 | 依赖黑名单通过；21 模块与 112 端点逐项验收 |

M17 只保留 `ad_*` 数据模型、开关字段和 `AdSlotProvider` 空实现。实现不调用广告请求端点，也不声明广告 SDK 组件。其余模块应按 `01` 的组件清单补齐。

## 6. 测试与验收

每个切片应包含 JVM 单元测试、Android 仪器测试和真机验证。金额、去重、规则、迁移、契约字段和同步重试应有单元测试。权限、通知监听、截屏、OCR、闹钟、小组件和支付回调应有仪器测试或真机操作记录。

每个切片应执行以下门禁：

1. 运行 `kmp` 的 Gradle 构建与 `:shared:jvmTest`。
2. 运行 `:app:assembleDebug`。
3. 在真机安装 APK，并打开该切片的新页面。
4. 截图或使用 `uiautomator dump` 记录页面结果。
5. 更新 `docs/接口实现对照表.md`、`docs/功能页面对照.md` 和 `spec/features.md`。
6. 运行 `git diff --check`。
7. 提交并推送到 `origin`。

全量验收应核对 `01` 的 21 个模块、`03` 的 112 个端点、约 100 个业务页、`02` 的表清单和 `AGENTS.md` §6 的 10 条纪律。任一页面的交互纪律未通过时，该模块不得标记完成。

## 7. 文档与提交

实现应更新 `AGENTS.md` §10 和 §11。§10 不得再用 Web 等价实现替代 Android 系统能力；§11 应把 KMP 构建、测试、真机验证和 112 端点对照表列为完成依据。README 应写明 `kmp/` 是唯一交付物，并说明无广告约束。

`docs/superpowers/specs/` 保存设计文档，`docs/superpowers/plans/` 保存实施计划。每个切片应在计划中记录状态。提交前应运行构建命令并读取完整输出；不得把管道末端的退出码当作 Gradle 成功。提交信息应使用简短中文，并推送当前分支。

## 8. 开放差异

以下事项缺少一手证据，实现应按可替换配置处理，并在文档中记录：

- 混淆后的 `@Body Map<String, Object>` 个别字段名未取到。
- 服务端真实响应、错误码和鉴权续期未抓包验证。
- 两个 `Bills/ImportBills` 重载的服务端区分条件未实测。
- `Config/GetV2` 的跨域鉴权行为未实测。
- 支付宝、微信、Google Billing 与运营商一键登录需要真实商户资质；本地测试应使用沙箱或可替换的支付网关。
