# GLASS_RESULT — feat/kmp-glass-theme（毛玻璃主题收尾）

日期：2026-11-14（本轮增量复核：12:19 UTC） · 分支：`feat/kmp-glass-theme`（仅此分支，未合并、未触碰其它分支） · JDK 17.0.21 / Gradle 8.11.1

## 1. 接手时未完成的部分

上一个流水线在 WIP 提交 `d00d45d`（`wip(kmp): 毛玻璃主题（未完成，流水线中途停止，未测试）`）之后中途停止，工作区留有一批未提交的改动：

- **未落地/未提交的类型阶梯**：`kmp/ui/src/commonMain/kotlin/com/meow/ledger/ui/theme/AppTypography.kt` 是未被 git 跟踪的新文件，README 路线图却已把「玻璃质感与字阶落地」划掉。
- **玻璃面重构未完成**：`theme/GlassSurface.kt` 被重写为「外层 Box 画玻璃面 → 空 Box 采样背板模糊 → 内容保持清晰」，但残留一个无法解析的 `import androidx.compose.foundation.layout.matchParentSize`。
- **页面迁移未完成**：`EntryNavHost`、`AutoHome`、`AutoPages`、`BillDetail`、`BillEntry`、`BillsHome`、`Dock`、`BatchPage`、`Exchange`、`Plans`、`StatsDetail`、`StatsHome` 已改成 `GlassPanel` / `glassCard` / `GlassChip` / `AppTypography`，但 `Plans.kt` 的 `ChoiceChip` 被加了第四个参数 `modifier: Modifier = Modifier`（放在 `onClick` 之后），导致全仓 50 多处尾随 lambda 调用把 lambda 绑到 `modifier`、`onClick` 缺参；`BillEntry.kt` 与 `BillDetail.kt` 迁移时删掉了仍在使用的 `size` / `RoundedCornerShape` / `border` / `clip` import。
- **未测试且验证记录失真**：`:app:assembleDebug` 在 `:ui:compileDebugKotlinAndroid` 以 exit 1 失败；`docs/功能差距表.md` 却写着构建 / JVM 测试通过；`:shared:jvmTest` 是 `UP-TO-DATE`（只是缓存命中，没有真正执行）；`GLASS_RESULT.md` 缺失；`.chain/test-cmd` 缺失。

Codex 第 1 轮 review 的 4 条 REQUEST_CHANGES 与上面对应，上一轮逐条处理并提交 `9b5e8b2`。本轮（HEAD 之后的增量）只收口一个遗留视觉回归：`GlassChip` 的胶囊宽度。

## 2. 完成的内容（上一轮 `9b5e8b2` + 本轮增量，逐项标明）

| 文件 | 改动 |
|---|---|
| `kmp/ui/src/commonMain/kotlin/com/meow/ledger/ui/theme/GlassSurface.kt` | **本轮新增修复**：`GlassChip` 的内容 Box 去掉 `Modifier.fillMaxWidth()`，胶囊恢复「文字 + 左右 18dp 内边距」的 wrap-content 宽度，点击区仍是整颗胶囊；补 KDoc 说明为何不填满可用宽度。修复前，在 `Budget.kt:128`、`AccountPages.kt:407` 等把 chip 直接放进 `Column` 的场景，以及 `Plans.kt:199`、`BillsHome.kt:163` 等横向 `Row` 场景，首颗 chip 会吃掉整行剩余宽度 / 拉伸成通栏，破坏灯影「胶囊 chip 横向排布」的语言。`Box` 的 `propagateMinConstraints` 默认 false（已核对 CMP 1.7.3 的 `BoxMeasurePolicy` 字节码），故去掉后父级 Column/Row 的最小宽度不会强加给胶囊；需要定宽仍可由调用方传 `modifier`。上一轮同一文件还删掉了错误的 `matchParentSize` import。 |
| `kmp/ui/src/commonMain/kotlin/com/meow/ledger/ui/manage/Plans.kt` | **上一轮**：`ChoiceChip` 恢复为 3 参数 `(label, selected, onClick)`，内部委托 `GlassChip`；修好 50 多处尾随 lambda 调用方（`AccountPages`、`Assets`、`Books`、`Budget`、`Catalog`、`FixedBills`、`Search`、`Widgets`、`StatsDetail` 等）。 |
| `kmp/ui/src/commonMain/kotlin/com/meow/ledger/ui/theme/GlassSurface.kt` | **上一轮**：删掉错误的 `matchParentSize` import。`matchParentSize()` 是 `BoxScope` 的成员扩展，在 `Box` 内容 lambda 内可直接调用、无需 import；保留它而不是换成 `fillMaxSize()`：滚动列表里 Box 的高度约束是无界的，`fillMaxSize` 会让模糊采样层变成 0 高度，`matchParentSize` 才会在其它子内容测量完后按父级最终尺寸测量。KDoc 已写明这一点。 |
| `kmp/ui/src/commonMain/kotlin/com/meow/ledger/ui/bills/BillEntry.kt` | **上一轮**：补回 `border`、`size`、`RoundedCornerShape` import（返回按钮方块与 `ActionButton` 仍在使用）；确认大括号配对无误（lexer 检查最终深度 0），review 提到的 `:180` 并非多余括号。 |
| `kmp/ui/src/commonMain/kotlin/com/meow/ledger/ui/bills/BillDetail.kt` | **上一轮**：补回 `androidx.compose.ui.draw.clip` import。 |
| `kmp/ui/src/commonMain/kotlin/com/meow/ledger/ui/theme/AppTypography.kt` | **上一轮**：纳入版本控制：8 档 11–20sp 字阶 + `LocalTypography` / `object AppTypography` / `semiBold()` / `bold()`。 |
| `kmp/ui/src/commonMain/kotlin/com/meow/ledger/ui/theme/Glass.kt`（**上一轮保留**）、`GlassBackdrop.kt`、`AppTheme.kt`、`kmp/ui/src/androidMain/kotlin/com/meow/ledger/ui/theme/GlassBackdrop.android.kt` | 保留 WIP 的 10 个玻璃 token、`LightGlass` / `DarkGlass`、`MeowTheme(theme)` 三态注入，以及 Android 12（API 31+）`RenderEffect` 12dp 模糊 + 1.6× 饱和、API 30 及以下 / JVM 无模糊退化的实现；随编译验证一并落地。 |
| 12 个页面文件（**上一轮保留**）（`EntryNavHost`、`AutoHome`、`AutoPages`、`BillDetail`、`BillEntry`、`BillsHome`、`Dock`、`BatchPage`、`Exchange`、`Plans`、`StatsDetail`、`StatsHome`） | 保留 WIP 的玻璃化迁移：卡片走 `GlassPanel` / `glassCard`，胶囊走 `GlassChip`，文字走 `AppTypography`，颜色仍由 `LocalPalette` / `LocalGlassScheme` 提供，无硬编码颜色。 |
| `docs/功能差距表.md:141`、`:142`、`:163` | **上一轮**：去掉与失败日志矛盾的「通过」表述，改为当时**实测**的命令、任务执行状态和数字（44 套件 / 220 用例 / 0 失败；`:app:assembleDebug` BUILD SUCCESSFUL），并保留「待证据」状态与真机待验证项。 |
| `README.md:79`、`docs/风格对照.md` | **上一轮**：路线图与风格对照补上「构建验证 / 观感待真机」的边界，避免把编译通过写成视觉验收通过。 |
| `GLASS_RESULT.md` | **上一轮新建 / 本轮更正**：补齐「未完成 / 完成 / 构建结果 / 真机限制 / 未做的事」，并更正远端推送状态与重复表述。 |
| `.chain/test-cmd` | **上一轮新建、本轮验证仍正确**：可执行验证脚本（见下），本轮未改动内容。 |

本轮实际改动仅 `kmp/ui/src/commonMain/kotlin/com/meow/ledger/ui/theme/GlassSurface.kt` 与 `GLASS_RESULT.md`；表中其余条目是上一轮 `9b5e8b2` 已提交的内容，保留在这里是为了完整回答「WIP 里到底缺什么」。两轮都只改玻璃主题相关文件与验证记录；没有改动业务功能，没有新增广告 SDK / 广告调用 / 广告请求端点（已 grep `kmp/*.gradle.kts`、`libs.versions.toml`：无 ADN 依赖），无广告开关变更。

## 3. 构建与测试结果（本轮实测，非缓存）

**本轮最终验证（胶囊宽度修复之后）**：从仓库根执行唯一验证脚本 `/tmp/meow-glass/.chain/test-cmd`（内容见 §6 的脚本说明），完整日志在仓库外的 `/tmp/glass-verify-run2.log`。脚本 `EXIT=0`：

1. `:ui:compileDebugKotlinAndroid` → **BUILD SUCCESSFUL in 21s**（本轮改动重新编译，非 `UP-TO-DATE`）。
2. `:shared:jvmTest --rerun-tasks` → **BUILD SUCCESSFUL in 25s**；`> Task :shared:jvmTest` **executed**（不是 `UP-TO-DATE`）。解析 `kmp/shared/build/test-results/jvmTest/*.xml`：**44 套件 / 220 用例 / 0 失败 / 0 错误 / 0 跳过**。`--rerun-tasks` 强制重跑，而非读缓存。
3. `:app:assembleDebug` → **BUILD SUCCESSFUL in 33s**；`:app:mergeExtDexDebug` / `:app:packageDebug` / `:app:assembleDebug` 全部执行，`kmp/app/build/outputs/apk/debug/app-debug.apk` 已重新生成（64,495,291 字节，时间戳 2026-11-14 12:19:17 UTC）。
4. `git diff --check` → exit 0。

上一轮（`9b5e8b2`）的实测日志分别为 `/tmp/meow-glass-jvmtest-fresh.log`、`/tmp/meow-glass-assembledebug-lowmem.log`、`/tmp/meow-glass-glass-theme-full-build.log`、`/tmp/meow-glass-assembledebug-verify.log`（均在仓库之外，不提交）；当时 `:app:assembleDebug` BUILD SUCCESSFUL、APK 64,414,342 字节。本轮数字与 APK 时间戳以 §3 开头的新一次执行为准。

**过程中的一次环境失败（如实记录）**：直接用默认参数跑 `:shared:jvmTest :app:assembleDebug --rerun-tasks`，以及单独跑 `:app:assembleDebug --rerun-tasks` / 增量 `:app:assembleDebug`，三次都在 `:app:mergeExtDexDebug` 阶段报

```
FAILURE: Build failed with an exception.

* What went wrong:
Gradle build daemon disappeared unexpectedly (it may have been killed or may have crashed)
```

原因是这台 8 GB 主机上还有其它会话的 Gradle daemon（约 630 MB）与 KotlinCompileDaemon（约 1.4 GB）常驻，可用内存约 1.6–2.0 GB，而 `kmp/gradle.properties` 给 Gradle 配了 `-Xmx2g`；编译任务本身都是 `UP-TO-DATE`/成功，崩溃发生在 AGP 的 dex 合并。把本构建的 Gradle 堆降到 1g、Kotlin 编译改为 in-process、关闭 daemon 后 `:app:assembleDebug` 稳定通过。未修改仓库的 `gradle.properties`（不想引入与主题无关的配置改动）；`.chain/test-cmd` 使用可通过的低内存参数。

## 4. 真机才能验证的部分（本轮没有验证，也未伪造）

- Android 12 / API 31+ 上 `RenderEffect` 的 12dp 背板模糊 + 1.6× 饱和观感、圆角裁切是否溢出、滚动时的性能。
- API 30 及以下退化为「无模糊玻璃面」时的可读性与层次感。
- 跟随系统 / 浅色 / 深色三态切换是否即时生效并在重启后保持；深浅两套 `GlassScheme` 的对比度、sheen / 阴影 / 描边在实际屏幕上的效果。
- 底部 Dock 高亮、胶囊 chip 选中态、卡片玻璃面在真实布局中的叠加顺序（代码上由 `matchParentSize` 保证采样层尺寸，但视觉结果必须看设备）。
- 无 Android 仪器测试（`kmp/app/src/androidTest/` 为空且被忽略），没有安装 APK 或截图。

## 5. 未完成 / 未做的事

- **本会话没有 push 分支**：本会话的 Rules 明确写「Do not push. Do not merge. Do not force-push.」，与任务正文的 step 5 冲突时按外层 Rules 执行；本轮改动提交在本地 `feat/kmp-glass-theme`。历史记录更正：上一轮流水线的 `push.out` 显示 `d00d45d..9b5e8b2 HEAD -> feat/kmp-glass-theme`（EXIT=0），且本轮 `git rev-parse origin/feat/kmp-glass-theme` = `9b5e8b28fce412929f6cfd5eb8279fb033d23047` = 本地 HEAD，故 `9b5e8b2` 及之前的 WIP 收尾**已经**在远端分支上；只有本轮新增 commit 尚待有权的一方按流程发布。上一版把「没有 push」写成整条分支未发布，不准确，此处更正。
- **本轮未合并**、未触碰 `master` 或任何其它分支，未使用 `gh`。历史合并 `05c11a1`「Merge remote-tracking branch 'origin/master' into feat/kmp-glass-theme」是接手前既有的提交，`origin/master` 已是 HEAD 的祖先。
- 未在本会话重复第 1 步里的 `git clone` / `git pull` / `merge origin/master`：接手时 HEAD `9b5e8b2` 已含合并 `05c11a1`，本地与 `origin/feat/kmp-glass-theme` 完全一致（ahead 0 / behind 0），无冲突可解；重复 clone 会丢失工作区里未提交的 WIP 收尾内容，因此直接用现有检出。
- 未新增名为 `Theme.kt` 的封装或 `ThemeMode` 枚举（三态仍走 `SettingsStore.KEY_THEME` + `MeowTheme`）；历史页面里局部 `fontSize = N.sp` 的全量替换、玻璃主题之外的 S8 广告依赖黑名单脚本、仪器/真机测试、真实自建后端联调仍是 `docs/功能差距表.md` 里的既有缺口，不属本轮范围。
- 没有新增自动化测试：`--rerun-tasks` 证明 220 个既有 JVM 用例在本分支重新执行通过，但玻璃渲染本身没有 JVM/仪器用例可覆盖（见 §4）。`:ui` 模块目前没有 jvmTarget 与 test 依赖，加测量/截图测试会引入超出「收尾玻璃主题」范围的新构建配置。

## 6. 唯一验证脚本（Pi 执行入口）

`.chain/test-cmd`（已跟踪、可执行、本轮未修改）内容如下，三条 Gradle 命令均通过 `java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain` 在 `kmp/` 下执行，并带低内存参数（原因见 §3）：

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO/kmp"

GRADLE=(java
  -Dorg.gradle.jvmargs="-Xmx1g -XX:HeapBaseMinAddress=40g -Dfile.encoding=UTF-8"
  -Dkotlin.compiler.execution.strategy=in-process
  -cp gradle/wrapper/gradle-wrapper.jar
  org.gradle.wrapper.GradleWrapperMain)

"${GRADLE[@]}" :ui:compileDebugKotlinAndroid --no-daemon --no-parallel --max-workers=1 --console=plain
"${GRADLE[@]}" :shared:jvmTest --rerun-tasks --no-daemon --no-parallel --max-workers=1 --console=plain
"${GRADLE[@]}" :app:assembleDebug --no-daemon --no-parallel --max-workers=1 --console=plain

cd "$REPO"
git diff --check
```

执行 `./.chain/test-cmd` 即可复现本轮 §3 的全部结果；`--rerun-tasks` 保证 JVM 测试不是缓存命中。
