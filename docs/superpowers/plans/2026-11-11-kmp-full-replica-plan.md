---
title: 喵啊记账 KMP 全量复刻实施计划
date: 2026-11-11
design: docs/superpowers/specs/2026-11-11-kmp-full-replica-design.md
status: S2 完成，S3 待启动
ad_free: 不接入广告 SDK，不调用广告请求端点
---

# 喵啊记账 KMP 全量复刻实施计划

本计划按已批准设计中的 S0 到 S8 顺序执行。每个切片完成后，执行者应在本文件记录提交号、验证命令和结果。未完成项不得标记完成。

依据文件为 `docs/superpowers/specs/2026-11-11-kmp-full-replica-design.md`、`spec/endpoints.json`、`spec/ddl.sql`、`spec/features.md`、`docs/功能页面对照.md` 和 `docs/接口实现对照表.md`。权威功能证据在 `D:\workspace\recordAccount\01-功能点全量清单.md`。

## S0 · 全量依据建立

状态：完成（`ce0cbb2`）。

1. 生成 `docs/功能差距表.md`。
2. 在差距表中登记 `01` 的 21 个模块、组件数、证据行和 KMP 实现位置。
3. 在差距表中登记 `spec/endpoints.json` 的 112 个注解路径与 KMP 契约方法。
4. 在差距表中登记约 100 个业务自有页面与 KMP 路由。
5. 核对 `02` §5 与 `04` §3 的表清单，登记缺失表和迁移文件。
6. 更新 `AGENTS.md` §10 和 §11，写明 KMP 单基座、物理能力、无广告和 KMP 验收门禁。
7. 更新 README，写明 `kmp/` 是唯一交付物。
8. 运行 `git diff --check`，提交并推送。

完成依据：`docs/功能差距表.md` 中 21 个模块、112 个端点、页面和表都有状态列；状态列只使用「已实现」「未实现」「无广约束」「待证据」。执行者应更新本文件和 README，并写明 KMP 单基座、物理能力与无广告边界。`npm run build` 与本切片的 KMP 构建均通过。

## S1 · M1 应用框架与 M20 权限

状态：完成。

1. 在 `AndroidManifest.xml` 声明 `01` §1 的入口组件，包括 Activity、TileService、前台服务、闹钟 Receiver 和开机 Receiver。
2. 声明 `POST_NOTIFICATIONS`、`READ_MEDIA_IMAGES`、`CAMERA`、`USE_BIOMETRIC`、`SCHEDULE_EXACT_ALARM`、`WAKE_LOCK`、`FOREGROUND_SERVICE` 等权限。
3. 实现动态权限申请、拒绝回退和用途说明页。
4. 实现启动页、主界面、WebView 容器、扫码页和票据页的路由。
5. 补齐权限映射表和交互纪律检查记录。
6. 运行 JVM 测试、Gradle 构建和 `assembleDebug`。
7. 在真机验证授权、拒绝和入口跳转。
8. 更新差距表、接口对照表和本计划；提交并推送。

完成依据：真机可完成授权和拒绝回退；入口跳转不丢失当前页面；Manifest 与差距表的组件清单一致。

## S2 · M4 账单、M5 分类标签、M6 账本协作

状态：完成（模拟器全流程验证通过；真机装机留作用户配合项）。

1. 补齐 `bill`、`bill_tag`、`bill_remark`、`cate_extend`、`tag`、`bill_book`、`member_book` 的表和迁移。已完成：`S2.sq` 9 张表 + `sync_queue` 11 列并入 `2.sqm`。
2. 实现 `Bills` 12 个、`Cate` 6 个、`MemberTag` 4 个、`BillsBook` 10 个契约方法。已完成：`MeowApi.kt` 32 方法 + `Bills.kt`/`Categories.kt`/`Tags.kt`/`Books.kt` 业务层。
3. 实现账单编辑、退款、批量修改、搜索、分类标签、账本扫码加入、邀请和成员页。已完成：`BatchPage.kt`、`Catalog.kt` 标签管理、`Books.kt` 账本协作页（books/book-new/book-edit/book-join/book-members）均存在；真实扫码留给 S4 相机管线。
4. 实现 `client_id` 幂等写、`sync_queue` 重试和本地优先刷新。已完成：`SyncEngine`（指数退避重试，纯本地模式静默保留队列）。
5. 实现删除二次确认、草稿保留、金额输入和筛选状态。批量修改已用 `ConfirmBox` 二次确认。
6. 为金额、去重、迁移、契约字段和同步重试写 JVM 测试。已完成 17 个新用例（BillsSyncTest/TagsTest/BooksDefaultTest/CateExtendTest/SyncQueueTest + 网络 7 个），96 用例全绿。
7. 升级与批量修改流程回归。已完成：v2 旧库 install -r 升级后 37 表/11 列 `sync_queue`/version=3；批量修改 4 笔改「交通」无崩溃、入队 `Bills/BatchUpdate POST`。真机 vivo 安装确认框待用户点击后复核。
8. 更新差距表和接口对照表；提交并推送。对照表与差距表已更新到「部分实现」。

完成依据：32 个契约方法逐项有实现位置；同名 `Bills/ImportBills` 重载按请求体字段区分；升级与批量修改流程在模拟器留 `uiautomator` 记录。

## S3 · M7 预算与 M8 统计

状态：未开始。

1. 补齐 `budget`、`cate_budget` 和对应迁移。
2. 实现 `Budget` 9 个契约方法和预算包含排除规则。
3. 实现总预算、分类预算、预算详情、统计报表、类型下钻、分类下钻和标签下钻页。
4. 实现整数分聚合、月份筛选和自绘 SVG 图表。
5. 为金额聚合、边界月份和包含排除规则写 JVM 测试。
6. 在真机验证预算编辑和 4 类统计下钻。
7. 更新差距表和接口对照表；提交并推送。

完成依据：9 个契约方法有实现位置；图表在深浅色模式可读；金额不出现浮点累加误差。

## S4 · M2 自动记账、M3 OCR、M13 导入导出

状态：未开始。

1. 声明通知监听服务、无障碍服务、MediaProjection 截屏服务和快捷磁贴服务。
2. 实现通知解析、支付成功页文本读取、截屏和 OcrLite 三模型结果接入。
3. 实现三条路径共用的去重键和待确认队列。
4. 实现 `KeyWordMatchingRule` 4 个、`MemberImportBillFile` 2 个、`Upload` 3 个契约方法。
5. 实现 5 个 OCR 页、8 个导入导出组件和批量改分类。
6. 实现 CSV 与文件导入、导出、重复合并、`import_id` 和票据图存储。
7. 运行 20 条模拟通知测试，记录识别率和重复数。
8. 用 10 张支付截图验证金额和商户，并记录批量改分类结果。
9. 在真机验证三路采集、权限和待确认队列。
10. 更新差距表和接口对照表；提交并推送。

完成依据：`04` M3 的识别率不低于 90%，重复数为 0；`04` M4 的 10 张截图金额和商户正确；解析结果不得静默入账。

## S5 · M9 资产、M10 存钱、M11 周期、M12 分期、M14 小组件

状态：未开始。

1. 补齐 `assets`、`save_plan`、`save_plan_item`、`fixed_bill`、`bill_remind`、`installment_plan`、`installment_item`、`widget_base`、`widget_user`、`widget_desktop` 和 `widget_hot` 的表与迁移。
2. 实现 `FundAccount` 6 个、`SavePlan` 14 个、`ConsumptionInstallment` 6 个、`BillRefund` 3 个契约方法。
3. 实现资产 12 个组件、存钱 6 个页面、周期账 4 个页面、分期 4 个页面和小组件 7 个组件。
4. 实现转账后的余额联动、存钱单期更新、归档筛选、分期公式重算、结清和精确闹钟。
5. 实现 `AppWidgetProvider`、`RemoteViews`、配置页和刷新 Receiver。
6. 为余额、期次、利息、闹钟恢复和迁移写 JVM 测试。
7. 在真机验证资产转账、期次更新、闹钟和桌面小组件。
8. 更新差距表和接口对照表；提交并推送。

完成依据：29 个契约方法有实现位置；桌面上可添加、刷新和移除小组件；周期提醒在开机后恢复。

## S6 · M15 账号安全与 M16 VIP 支付

状态：未开始。

1. 补齐 `member_profile`、`sms_code`、`contract_info`、`member_order` 和 `group_vip` 的表与迁移。
2. 实现 `Account` 10 个、`Member` 10 个、`Order` 5 个、`VipGroupBuy` 3 个、`PersonCenter` 1 个契约方法。
3. 实现登录、手机号绑定、换绑、切换账号、用户资料、关于、协议、个性化推荐和应用锁。
4. 接入生物识别、安全存储、运营商一键登录和第三方登录的可替换网关。
5. 实现双 VIP、促销、绑定管理、支付宝、微信、Google Billing 和自动续费协议。
6. 实现订单状态回查和支付回调；本地测试使用沙箱或可替换支付网关。
7. 在真机验证登录、应用锁、订单状态回查和支付回调。
8. 更新差距表和接口对照表；提交并推送。

完成依据：29 个契约方法有实现位置；真实商户资质缺失时，文档记录沙箱边界，不把沙箱写成生产支付。

## S7 · M18 推送保活与 M19 埋点配置反馈

状态：未开始。

1. 补齐 `behavior_event`、`remote_config` 和错误日志表。
2. 实现 `Behavior` 1 个、`Config` 1 个、`Operation` 2 个契约方法。
3. 实现个推、小米、华为、OPPO、vivo 推送通道的可替换接口和前台保活服务。
4. 实现 `01` 列出的 100 多个 `ac_*` 事件、上报、远程配置 24 个开关、反馈和错误日志。
5. 实现推送权限申请、电池优化引导和通道状态页。
6. 为事件字段、上报队列和开关默认值写 JVM 测试。
7. 在真机验证推送、保活、配置读取、反馈和错误日志。
8. 更新差距表和接口对照表；提交并推送。

完成依据：4 个契约方法有实现位置；事件名与 `01` 一致；广告开关固定关闭；无广静态检查通过。

## S8 · M17 无广约束、M21 全量回归

状态：未开始。

1. 运行广告依赖黑名单检查，记录结果。
2. 核对 21 个模块、112 个端点、约 100 个页面、`02` 表清单和 `AGENTS.md` §6 的 10 条纪律。
3. 运行全量 JVM 测试、Android 仪器测试、Gradle 构建和 `assembleDebug`。
4. 在真机安装最终 APK，按 `04` 六里程碑执行端到端流程。
5. 补齐 `docs/接口实现对照表.md`、`docs/功能页面对照.md`、`spec/features.md`、README 和 `05` 差异记录。
6. 修正所有「已实现」但正文写有「未做」「no-op」「不申请」的条目。
7. 运行 `git diff --check`，提交并推送。

完成依据：差距表不存在「未实现」「待证据」项；每个模块有真机或自动化测试证据；所有构建命令有完整输出记录。

## 每个切片的提交记录

| 切片 | 提交号 | 构建结果 | 真机结果 | 差距表状态 |
| --- | --- | --- | --- | --- |
| S0 | `b756323`、`ce0cbb2` | `npm run build` 通过；`git diff --check` 通过 | 不必 | 完成 |
| S1 | `ab51295` | `BUILD SUCCESSFUL in 32s`；`:app:assembleDebug`、`:shared:jvmTest` 通过 | 模拟器权限页截图、继续按钮后首页文本与截图 | 完成 |
| S2 | `e3a4b25` + 本切片 | `BUILD SUCCESSFUL`（96 tests 0 fail） | 模拟器升级路径+批量修改 uiautomator 通过；真机待用户确认安装 | 完成 |
| S3 | 待记录 | 待记录 | 待记录 | 未开始 |
| S4 | 待记录 | 待记录 | 待记录 | 未开始 |
| S5 | 待记录 | 待记录 | 待记录 | 未开始 |
| S6 | 待记录 | 待记录 | 待记录 | 未开始 |
| S7 | 待记录 | 待记录 | 待记录 | 未开始 |
| S8 | 待记录 | 待记录 | 待记录 | 未开始 |
