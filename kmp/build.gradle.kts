plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
}

// ---------------------------------------------------------------------------
// S8 门禁：广告 ADN / ADX 依赖黑名单（见设计文档 §1 / §2 line 40、AGENTS.md §11）
//
// 复用仓库根 scripts/check-ad-blacklist.sh，避免 Gradle 与 CI 各写一套规则。
// 命中黑名单坐标时脚本非零退出 -> Exec 任务失败 -> check/build 失败。
// 洁净树上脚本返回 0，不影响 :shared:jvmTest 与 :app:assembleDebug。
// ---------------------------------------------------------------------------
val repoRootDir = rootProject.projectDir.parentFile
val adBlacklistScript = rootProject.file("../scripts/check-ad-blacklist.sh")

val checkAdBlacklistSelfTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "S8 门禁自测：临时夹具证明黑名单脚本命中即失败、洁净树通过（不联网）"
    commandLine("bash", adBlacklistScript.absolutePath, "--self-test")
    outputs.upToDateWhen { false }
}

val checkAdBlacklist by tasks.registering(Exec::class) {
    group = "verification"
    description = "S8 门禁：扫描 kmp/ 依赖声明中的广告 ADN / ADX 坐标，命中即构建失败"
    commandLine("bash", adBlacklistScript.absolutePath, repoRootDir.absolutePath)
    inputs.file(adBlacklistScript)
    outputs.upToDateWhen { false }
}

// 把门禁挂到常规验证任务上：gw check / gw build（含各子项目 :check）都会先过门禁。
// 只挂 check，不改 assemble*/jvmTest 的任务图，洁净树上它们的执行路径不变。
allprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(checkAdBlacklistSelfTest, checkAdBlacklist)
    }
}
