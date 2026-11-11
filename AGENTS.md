# AGENTS.md — 喵啊记账 · 自动记账 App 复刻工程

> 本文件是所有在本仓库工作的 Agent 的**唯一入口约定**。任何模型开始动手前先读完本文件。
> 用户全局约定见 `C:\Users\HANXIAO\.claude\CLAUDE.md`、`RTK.md`；本仓库的 `CLAUDE.md` 直接 `@AGENTS.md`。

## 0. 任务背景（必读）

- 目标：在 `D:\workspace\recordAccount\works`（本仓库）用 `kmp/` 单基座**物理复刻**上一级目录 `D:\workspace\recordAccount\` 的 `base.apk` 的全部功能与全部接口，并保持 App 无广告。设计、实施计划与逐项状态见 `docs/superpowers/specs/2026-11-11-kmp-full-replica-design.md`、`docs/superpowers/plans/2026-11-11-kmp-full-replica-plan.md` 和 `docs/功能差距表.md`。
- 样本身份：`com.android.miaoa.achai` v1.8.1「喵啊记账 / puppy」类**自动记账 Android App**（不是影视播放器）。
- 上一级目录的交付文档（一手证据，按可信度排序）：
  - `00-总览与结论.md` — 身份、证据链、复刻路线
  - `01-功能点全量清单.md` — 21 个功能模块 / 约 100 个业务页面，每条带 Activity / 表 / 权限证据
  - `02-业务架构.md` — 分层架构、数据流、20+ Room 表、SDK 矩阵
  - `03-接口文档.md` — 业务 API / 配置 / 上报 / 广告 / 第三方 SDK 五类端点与数据契约
  - `04-复刻实现蓝图.md` — 技术选型、完整 DDL、模块拆分、6 个里程碑、自建后端接口契约
  - `05-证据与差异核对.md` — O/D/I/U 证据分级、失败清单、开放差异
  - `probe-result.md`（326 KB）、`_work/apk-strings.md`（87 KB）— 静态探针全文
  - `rea-artifact-evidence.json`（9.7 MB）— REA 归档清单 Evidence
  - `jadx_out/sources/` — jadx 1.4.7 反编译源码（23,028 `.java`）
  - `apktool_out/` — 资源反编译
  - `screens/screen.png` — **灯影 App 截图**（仅作 UI 风格基准，非本样本功能证据）
  - `base.apk` — 样本只读副本（81 MB）
- 灯影风格源码（UI 基准，非功能来源）：`D:\workspace\dengY\DengYing\ui\src\commonMain\kotlin\com\dengying\player\ui\theme\`
  - `Color.kt`：`LightPalette` / `DarkPalette`（brand、appBg、cardBg、searchBg、textPrimary、textSecondary、divider、danger…）
  - `Glass.kt`：`LightGlass` / `DarkGlass`（fill、chipFill、chipSelectedFill、dockHighlight、border、shadow…）
  - `Theme.kt`：`ThemeMode`（跟随系统/浅色/深色）、`AppTypography`（11–20sp 字阶）、`DengYingTheme`
  - 截图 `screens/screen.png` 佐证：胶囊搜索框 + 横向 chip 标签 + 圆角卡片网格 + 玻璃底栏 Dock。

## 1. 硬性要求（用户原始要求，逐条不可打折）

1. **功能完整，不落接口**：应覆盖 21 个模块的全部功能面，并逐一实现 `03` §3 的全部业务端点；字段名应与样本一致。若发现逆向出的接口不完整，应使用 REA MCP 回到 `base.apk` 补取。
2. **UI 风格与灯影一致**：配色、玻璃质感、字阶、圆角、胶囊控件、底栏交互应对齐 `Color.kt` / `Glass.kt` / `Theme.kt` 与 `screens/screen.png`。交互逻辑也应对齐，并满足 `§6` 的删除确认、金额输入、草稿保留、空态出口与筛选条件规则。
3. **Git + 远程仓库**：本地 `git init`，用 `gh` 创建 **public** 远程仓库（已创建：`xiao-hans/meow-ledger`），每完成一个你认为的里程碑就 `commit` + `push`。
4. **开始实现前先写 `AGENTS.md` 和 `CLAUDE.md`**，`CLAUDE.md` 内容直接 `@AGENTS.md`。
5. `AGENTS.md` 除工程约定外，应写明 **skill 调度** 与 **MCP 调度**，让后续模型知道何时用哪个 skill / MCP。

## 2. 接口权威清单（不要凭记忆写）

- **唯一权威来源**：`D:\workspace\recordAccount\jadx_out\sources\com\android\miaoa\achai\api\a.java`（597 行）。
- 已从该文件机器提取到 `spec/endpoints.json`（112 个 `@POST`/`@GET` 注解路径 + 2 个动态 `@Url` 方法），字段名为 Retrofit `@Field` / `@Body` 声明的明文 key。
- 分组与数量：`Account 10`、`Member 10`、`Bills 12`、`BillsBook 10`、`Budget 9`、`Cate 6`、`FundAccount 6`、`ConsumptionInstallment 6`、`SavePlan 14`、`KeyWordMatchingRule 4`、`MemberTag 4`、`MemberImportBillFile 2`、`BillRefund 3`、`Order 5`、`VipGroupBuy 3`、`Upload 3`、`Operation 2`、`Behavior 1`、`Config 1`、`PersonCenter 1`。
- 两个同名重载：`Bills/ImportBills` 分别接受 `ImportBillsReq` 与 `ImportOcrBillsReq`（按 body 字段区分）。
- `Config/GetV2` 带 `@Headers({"urlType:configManage"})`，走配置域 `meow-config.aiyouaiyou.cn`，其余走主域 `puppy-api.cqmiaoa.com/api/`。
- 动态方法：`@Streaming @GET @Url`（文件下载，返回 `ResponseBody`）、`@POST @Url + MultipartBody`（OSS 直传，返回 `UploadOssData`）。
- `03` §2 的请求管线：form/body 字段被打包成 `data=<native 加密串>`；**自建后端不复现私有加密**，按 `04` §5 的明文 JSON 契约实现（功能等价）。统一响应 `{code:0, message:"", data:{...}, trace_id:"..."}`。
- 任何时候新增/修改接口，应同步更新 `spec/endpoints.json`、`docs/接口实现对照表.md` 与 `docs/功能差距表.md`，三者与 `api/a.java` 一致；KMP 实现位置写 `kmp/shared/network/`。

## 3. 数据模型（对齐 `04` §3 DDL）

- 核心表：`bill`（复合主键 `member_id + client_id`，`client_id` 为端侧幂等键）、`bill_book`、`member_book`、`category`、`cate_extend`、`budget`、`cate_budget`、`assets`、`tag`、`bill_tag`、`bill_remark`、`rule`、`refund_bill`、`installment_plan`、`installment_item`、`widget_base/user/desktop/hot`、`ad_data/info/config/adevent/click_event/trackurl` 等 20+ 张。
- `bill.type`：`1 支出 / 2 收入 / 3 转账`；`bill.source`：`0 手工 / 1 通知 / 2 截屏 / 3 OCR / 4 导入 / 5 周期`。
- `day` 为 `yyyyMMdd` 整数；时间格式 `yyyy-MM-dd HH:mm:ss`；金额**用整数分**（`amount_cents`）避免 `double` 累加误差（`04` §7.5）。
- **本地优先**：所有写操作先落本地库再同步，云端不可达时 UI 不阻塞（样本用复合主键的原因）。
- 自动记账去重键：`hash(sourceApp + money + occurredAt/60s + merchant)`，三路采集（通知/无障碍/截屏）共用，`04` §4.3 明确「去重是自动记账的生命线」。

## 4. 技术选型（KMP 单基座）

| 层 | 选型 | 说明 |
|---|---|---|
| 运行形态 | `kmp/` Kotlin Multiplatform + Compose Multiplatform Android App | 唯一交付物；`:app` 装配 `:shared` 与 `:ui` |
| 样式 | `kmp/ui/` 的灯影主题 token | 对齐 `Color.kt` / `Glass.kt` / `Theme.kt`，深浅色跟随系统 + 手动三态切换 |
| 本地库 | SQLDelight + `MeowDatabase` | 对齐 `02` §5 与 `04` §3；保留 `client_id` 幂等语义 |
| 网络 | Ktor Client + `MeowApi` | 在 `kmp/shared/network/` 实现 112 个端点、同字段名和统一响应封装 |
| 系统能力 | Android Service / Receiver / Provider 与动态权限 | 通知监听、无障碍、截屏 OCR、精确闹钟、小组件、推送与支付回调 |
| 图表 | Compose Canvas | 对齐灯影配色，深浅色均可读 |
| 图标 | 自绘矢量图标 | 不复制原 App 资源/图标/协议文本（`04` §7.4） |

`src/` 与 `android-shell/` 保留为规格参考，不参与交付验收。设计、实施计划与差距表见 `docs/superpowers/specs/`、`docs/superpowers/plans/` 和 `docs/功能差距表.md`。

## 5. 目录约定

```
works/
├─ AGENTS.md                 本文件（工程宪法）
├─ CLAUDE.md                 @AGENTS.md
├─ README.md                 项目说明、运行方式、里程碑状态
├─ spec/                     endpoints.json / ddl.sql / features.md
├─ docs/                     接口、页面、风格、差距表、设计与实施计划
├─ kmp/
│  ├─ app/                   Android 入口、Manifest、系统组件
│  ├─ shared/                db、network、domain、platform 与 Repository
│  └─ ui/                    Compose 页面、导航与灯影主题
├─ src/ + public/            Web 规格参考，不参与交付验收
├─ android-shell/            WebView 规格参考，不参与交付验收
└─ package.json / tsconfig.json / vite.config.ts / .gitignore
```

## 6. 交互纪律（防「反人类逻辑」）

1. 任何删除/解绑/退出账本/清空数据 → 二次确认对话框，明确写出后果。
2. 表单（记一笔、预算、资产、计划）在页面切换/返回时保留草稿，提交成功后才清空。
3. 金额输入用受控字符串 + 即时校验，不吞字符、不强制两位小数、不自动改数量级。
4. 每个空态应给出「下一步」按钮（去记一笔 / 去新建 / 去开启自动记账）。
5. 筛选（账本、月份、分类）显式展示当前值，切换不清空其它条件；「重置」是独立按钮。
6. 加载中/失败/成功三态齐全；失败可重试，不静默吞错。
7. 自动记账识别结果应进「待确认队列」，用户批量通过/修改后才写账，绝不静默入账（对齐 `BillIdentifiedActivity`）。
8. 深浅色切换即时生效且持久化；系统偏好只在「跟随系统」模式生效。
9. 主导航：底部 Dock 常驻，当前项高亮；二级页有明确返回入口；不出现「进得去出不来」的页面。
10. 危险操作红色用 `danger` token，禁止用品牌色充当警告色。

## 7. Skill 调度（模型应遵守）

> 何时调用哪个 skill，用 `Skill` 工具按名字调用；slash 命令同名时直接输入 `/<name>`。

| 场景 | 应调用的 skill | 理由 |
|---|---|---|
| 任何 UI/页面/组件/视觉实现或改版 | `frontend-design` → 再 `design-taste-frontend`（落地页/重设计场景） | 保证不走模板化 AI 观感，对齐灯影「反 slop」质感 |
| 新建图表/统计报表/仪表盘/数据可视化 | `dataviz`（**写第一行图表代码之前**） | 统一配色、可访问性、深浅色一致 |
| 把玻璃/浅色/深色主题做成可切换 | `theme-factory` | 主题化 token 管理 |
| 写/改 README、架构说明、接口对照表等技术文档 | `hai-simplified-technical`（简化技术中文） | 术语一致、句子短、无翻译腔 |
| 文档写完后核对「文档 vs 代码/接口清单」是否漂移 | `hai-audit-docs` | 防止 112 端点清单与实现脱节 |
| 文档被反复打补丁需要推倒重写 | `hai-rewrite-doc` | 按最新结论重建 |
| 修正/废弃某方案后写正式工程文档 | `no-negative-echo` | 禁止「此地无银三百两」式残留与编辑旁白 |
| 开发功能/修复/重构（任何增量） | `software-development-workflow` | 小步、可验证增量、先定 Definition of Done |
| 创造性工作（新功能/改行为）**动手之前** | `superpowers-brainstorming` | 先澄清意图与需求 |
| 2 个以上互不依赖的独立任务 | `superpowers-dispatching-parallel-agents` | 并行分派 |
| 声明「完成 / 已修复 / 测试通过」之前 | `superpowers-verification-before-completion` | 应先跑验证命令并看到输出 |
| 提交 / 建 PR 之前 | `superpowers-verification-before-completion` | 证据先于断言 |
| 需求是 TDD / 红绿重构 / 集成测试 | `tdd` | 测试先行 |
| 报告 bug、说「坏了/抛错/失败」、性能回归 | `diagnose` | 复现→最小化→假设→插桩→修复→回归 |
| 代码写完后做质量清理（复用/简化/效率） | `simplify` | 不找 bug，只做清理 |
| 审查当前 diff / PR | `code-review [low\|medium\|high]` | 正确性优先 |
| 配置 settings.json / hooks / 权限 | `update-config` | 自动化行为应走 hooks |
| PowerShell/cmd/长 Windows 路径出现字符丢失 | `win-shell-paths` | 防静默丢字符 |
| 用户说「跑起来/启动 App/截图确认」 | `run` | 在真实运行环境确认 |
| 问「怎么配置 Claude Code / 装什么 skill」 | `claude-code-guide` agent / `find-skills` | 用对工具 |
| 用户明确要求多 Agent 编排 / workflow | `Workflow`（先 `workflow-authoring`） | 需显式授权 |

**ADHD 输出约定**：`i-have-adhd` skill 全程生效 —— 回复先给「下一步动作」，多步工作编号，跨轮复述状态，去掉旁支，给时间估计，把阶段性成果显式写出来。用户说「stop adhd mode / normal mode」才关闭。

## 8. MCP 调度（模型应遵守）

| 场景 | 应调用的 MCP / 工具 | 说明 |
|---|---|---|
| 本仓库**没有** `.codegraph/`，理解/定位代码 | 内置 `Glob` / `Grep` / `Read` | 用户全局 CLAUDE.md 规定：无 `.codegraph/` 时跳过 CodeGraph；**索引由用户决定，不自行 init** |
| 有 `.codegraph/` 的仓库（如 `dengY/`）查符号 | `mcp__codegraph__codegraph_explore`（传 `projectPath`） | 一次调用拿到源码 + 调用路径，优于 grep 循环 |
| 逆向/核对 APK 接口、资源、Manifest、DEX 字符串 | **REA MCP**（`reverse-engineer-anything` skill 配套；未配置时自行配置） | 用户要求：发现接口不完整就再次用 REA MCP 补取。可用时优先于静态文档；不可用时回退到 `jadx_out/sources/com/android/miaoa/achai/api/a.java` 与 `probe-result.md`（二者即 REA 归档的一手证据） |
| 读大文件 / 统计 / 过滤 >20 行的输出（日志、build、大 JSON、大 md） | `mcp__plugin_context-mode_context-mode__ctx_execute`（python，打印答案而非原文） | 避免大结果灌进上下文；注意 `sys.stdout.reconfigure(encoding="utf-8")` 防 GBK 报错 |
| 从已索引的大输出里找被截断部分 | `mcp__plugin_context-mode_context-mode__ctx_search` | 配合 ctx_execute 的 `source` |
| 会话恢复 / 继续上次未完成工作 | `mcp__plugin_context-mode_context-mode__ctx_resume` | 会话开始先看有没有历史 |
| 查用户偏好/以往决策/环境事实 | `mcp__hermes-memory__memory_search`；保存耐久事实用 `memory_add` | 与 Pi agent 共用记忆库 |
| 查库/框架/SDK/CLI 的**当前**文档（React/Vite/TS/IndexedDB…） | `mcp__context7__resolve-library-id` → `mcp__context7__query-docs` | 训练数据可能过时，应用当前文档；优先于 WebSearch |
| 查 GitHub 仓库/PR/issue、创建远端 | `gh` CLI（已登录 `xiao-hans`） | 本仓库远端 `xiao-hans/meow-ledger` |
| 需要联网查非文档类资料 | `WebFetch` / `WebSearch`（经 ToolSearch 加载） | 库文档类优先 context7 |
| 上下文过长需要压缩 | `mcp__bili__compress` / `decompress` / `search_context` / `acp_status` | 保留 112 端点清单与决策理由，压缩已消费的输出 |
| 后台命令/等待外部状态 | `Monitor`（ToolSearch 加载） | 不用前台 sleep 轮询 |

**命令输出约定**：全局 `RTK.md` 生效 —— shell 命令会被 `rtk` 代理压缩输出，视为完整结果；结果为空/与退出码矛盾/乱码时才用 `rtk proxy <cmd>` 重跑。

## 9. 提交纪律

- 每个**自认为完成的一个里程碑**立即 `git add` → `git commit`（中文简短说明）→ `git push origin master`（或当前分支）。
- 提交前必跑对应验证命令：KMP 功能跑 Gradle 构建、`:shared:jvmTest` 与 `:app:assembleDebug`；纯文档提交跑 `npm run build` 和 `git diff --check`；失败不提交、不谎报通过。
- 提交信息末尾带本会话要求的 attribution 行（若系统提供）。
- 不得覆盖/删除上一级目录的任何文件；本仓库之外的写入需先确认。
- `base.apk`、jadx/apktool 产物、REA evidence 等大文件**不复制进本仓库**，只引用路径。

## 10. 开放差异与限制（诚实记录，见 `05`）

1. 源码级证据只到字节码，混淆类名使个别 `@Body Map<String,Object>` 的具体 key 未取（U）。
2. 没有动态抓包：服务端真实响应、错误码、鉴权续期未验证；`data` 密文内部字段顺序未实测。
3. `DataNative.encrypt/decrypt/getAutoBillKey` 在 so 内，密钥由 `lc/a.java:114-116` 注入。KMP 契约层发送明文 JSON，不实现私有算法与密钥。
4. App 保持无广告。6 家 ADN 与自有 ADX 不接入，广告请求端点不调用；`ad_*` 数据模型与 `AdSlotProvider` 空实现保留，`Config/GetV2` 的广告开关固定关闭。
5. 多厂商推送、埋点、支付与运营商一键登录使用可替换网关。真实商户资质缺失时，测试环境使用沙箱并在文档记录边界。
6. 协议/帮助 H5、CDN 图标、教程视频属原 App 资产，本复刻自行撰写文案与自绘图标。
7. 通知监听、无障碍、MediaProjection 截屏、精确闹钟、桌面小组件与前台服务由 Android 组件实现；模拟数据只用于自动化测试。
8. 开放差异与设计边界见 `docs/superpowers/specs/2026-11-11-kmp-full-replica-design.md` §8。

## 11. Definition of Done（每个里程碑）

- [ ] `spec/endpoints.json` 中该里程碑涉及的端点全部在 `kmp/shared/network/` 有实现，字段名一致。
- [ ] `docs/接口实现对照表.md` 与 `docs/功能差距表.md` 对应行标记状态、KMP 实现位置和验证方式。
- [ ] `docs/功能页面对照.md` 登记该里程碑涉及的页面与 KMP 路由。
- [ ] UI 用 `kmp/ui/` 灯影主题 token，无硬编码颜色；深浅色都检查过。
- [ ] 交互纪律 §6 的 10 条逐条过一遍。
- [ ] Gradle 构建、`:shared:jvmTest` 与 `:app:assembleDebug` 通过；Android 能力有仪器测试或真机验证输出。
- [ ] 广告依赖黑名单检查通过；无广告 SDK 与广告请求端点。
- [ ] `docs/superpowers/plans/2026-11-11-kmp-full-replica-plan.md` 记录提交号与验证结果。
- [ ] 已 commit + push。
