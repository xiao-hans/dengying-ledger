#!/usr/bin/env bash
# S8 门禁：广告 ADN / ADX 依赖黑名单检查。
#
# 依据：
#   - docs/superpowers/specs/2026-11-11-kmp-full-replica-design.md §1 / §2 / §5(S8)
#   - AGENTS.md §11 与 README S8 的 DoD：「广告依赖黑名单检查通过；无广告 SDK 与广告请求端点」
#
# 重要事实：设计文档只写「6 家广告 ADN 或自有 ADX SDK」（§1 line 19），
# 全仓库从未点名这 6 家的具体名字（已检索 docs/、spec/、README、AGENTS.md 及 kmp/）。
# 因此本脚本采用一份**保守、显式**的黑名单，覆盖业界常见的 Android 广告 SDK
# Maven group / artifact / Java 包名；它是实现者补充的工程判断，不是设计文档的原话。
# 点名的 6 家并未在设计文档中出现，请勿把下面的模式当成设计文档列出的厂商。
#
# 覆盖模式（见 BLACKLIST_PATTERNS）：
#   穿山甲/CSJ/Pangle/字节      : pangle, pangolin, openadsdk, bytedance, tt-*, csj-sdk, ttadsdk, csj-ad
#   优量汇/GDT/广点通/腾讯广告  : gdt, guangdiantong, tencent-gdt, qqe*, qq-e-ads, tads, tencent-ad
#   快手联盟/KSAD              : ksad, kwad, kuaishou-ad, kuaishouads, ksadsdk, kwai-ad
#   Sigmob                      : sigmob, windads, sigmob-ad, sigmobsdk
#   Mintegral                  : mintegral, mbridge, mtg-ad
#   TopOn / TradPlus / GroMore : topon, anythink, tradplus, gromore, gro-more
#   通用聚合/ADX/广告 SDK 标记   : adx, *-adx, *-ads-sdk, *-ad-sdk, ad-exchange, mediation-*, ads-mediation-*
#
# 有意不拦（必须保留、不得误报）：
#   - ad_* 数据模型 / 表（ad_data、ad_info、ad_config …）
#   - AdSlotProvider 空实现与「advertising / 广告」注释或文案
#   - Config/GetV2 的广告开关、Manifest 的 InterstitialAdActivity alias（no-op 深链兼容入口）
#   - 自动记账的采集来源 App 包名：com.tencent.mm / com.tencent.mobileqq / com.kuaishou.nebula
#
# 扫描范围：只扫描 Gradle 依赖声明文件（build.gradle/.kts、libs.versions.toml、
# 依赖锁定/校验文件、.properties、settings 里的坐标、AndroidManifest 之外的声明），
# 不扫描 Kotlin/Java 源码，避免把业务包名当成 SDK 依赖。
# 用法：check-ad-blacklist.sh [root]   root 缺省为本脚本所在仓库根目录。
#       --self-test 运行内置夹具自测：命中失败 / 洁净通过 / 真实树通过（hermetic，不联网）。

set -uo pipefail

# ---------- 路径解析 ----------
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"

self_test=0
root="${repo_root}"
for arg in "$@"; do
  case "$arg" in
    --self-test|--selftest|-t) self_test=1 ;;
    -*) echo "[ad-blacklist] 未知参数: $arg" >&2; exit 2 ;;
    *) root="$arg" ;;
  esac
done
root="$(cd -- "$root" 2>/dev/null && pwd)" || { echo "[ad-blacklist] 目录不存在: $root" >&2; exit 2; }
kmp_dir="${root}/kmp"

# ---------- 黑名单模式（ERE，大小写不敏感；逐行匹配依赖声明） ----------
# 每条都是 Maven group / artifact / 明确广告 SDK 包名片段，不会命中普通业务词。
BLACKLIST_PATTERNS=(
  'com\.bytedance|com\.pangle|pangolin|openadsdk|\bpangle\b|bytedance[-.]sdk|ttadsdk|csj[-_.]?(sdk|ad)|tt[-_.]?(sdk|ad)[-_.]?(sdk|ad)?'
  'com\.qq\.e|tencent[-_.]?gdt|\bgdt\b|guangdiantong|qqe(ads|sdk|union)|qq[-_.]e[-_.]?ads|\btads\b|tencent[-_.]?ad(s|sdk)'
  'com\.kuaishou\.(ad|ksad|kwad)|com\.kwai|kuaishou[-_.]?ad|\bksad\b|ksadsdk|kwai[-_.]?ad|\bkwad\b'
  'com\.sigmob|\bsigmob\b|windads|sigmob[-_.]?(sdk|ad)'
  'com\.mintegral|\bmintegral\b|\bmbridge\b|mtg[-_.]?ad'
  'com\.topon|\btopon\b|com\.anythink|\banythink\b|\btradplus\b|gro[-_.]?more|\bgromore\b'
  '(^|[:._ -])adx([-_.:]|$)|ad[-_.]?exchange|(^|[:._ -])ad[-_.]?sdk|(^|[:._ -])ads[-_.]?sdk|[-_.]ad[-_.]?mediation|mediation[-_.]sdk|ads[-_.]?mediation'
)

PATTERN_LABELS=(
  '穿山甲/CSJ/Pangle/字节'
  '优量汇/GDT/广点通/腾讯广告'
  '快手联盟/KSAD'
  'Sigmob'
  'Mintegral'
  'TopOn/TradPlus/GroMore 聚合'
  '通用 ADX/广告 SDK/聚合标记'
)

# 被扫描的依赖声明文件名（basename 匹配；只扫描 kmp/ 树下的 Gradle 相关文件）。
# 不扫描 *.kt / *.java / *.xml 源码，避免误伤业务包名与 ad_* 表。
is_dep_file() {
  case "$(basename -- "$1")" in
    build.gradle|build.gradle.kts|settings.gradle|settings.gradle.kts|gradle.properties|gradle.lockfile|*.gradle.lockfile|buildscript-gradle.lockfile|libs.versions.toml|gradle-wrapper.properties|gradle-dependencies.txt|dependency-check-report.*|verification-metadata.xml|*.pom) return 0 ;;
    *) return 1 ;;
  esac
}

# 收集要扫描的文件（跟随 find，排除构建产物 / 缓存 / VCS）。
collect_files() {
  find "$1" \
    \( -name .git -o -name .gradle -o -name build -o -name .kotlin -o -name node_modules -o -name .idea -o -name .chain -o -name .tmp \) -prune -o \
    -type f -print 2>/dev/null
}

# ---------- 核心检查 ----------
# 输出命中行到 stdout（格式: file:line:label:content）。
# 返回 0=扫描完成；非 0=读取/收集阶段出错（调用方必须按失败处理）。
scan_tree() {
  local base="$1" f line idx status=0
  local -a files=()
  while IFS= read -r f; do
    if is_dep_file "$f"; then files+=("$f"); fi
  done < <(collect_files "$base") || status=1

  for f in "${files[@]}"; do
    local lineno=0
    while IFS= read -r line || [[ -n "$line" ]]; do
      lineno=$((lineno + 1))
      # 跳过整行注释（# 或 //），避免把说明文字里的词当依赖。
      local trimmed="${line#"${line%%[![:space:]]*}"}"
      case "$trimmed" in \#*|\/\/*) continue ;; esac
      idx=0
      for pat in "${BLACKLIST_PATTERNS[@]}"; do
        if printf '%s\n' "$line" | grep -iEq -- "$pat"; then
          printf '%s:%d:%s:%s\n' "${f#"$root"/}" "$lineno" "${PATTERN_LABELS[$idx]}" "$line"
          break
        fi
        idx=$((idx + 1))
      done
    done < "$f" || status=1
  done
  return "$status"
}

# ---------- 自测（hermetic fixture） ----------
run_self_test() {
  local tmp pass=0 fail=0
  tmp="$(mktemp -d)" || { echo "[self-test] 无法创建临时目录" >&2; return 2; }
  trap 'rm -rf -- "$tmp"' RETURN

  # 用一个隔离的假仓库根：复制脚本，放一份含黑名单坐标的 build.gradle.kts。
  mkdir -p "$tmp/fake/scripts" "$tmp/fake/kmp/app"
  cp -- "$0" "$tmp/fake/scripts/check-ad-blacklist.sh"
  chmod +x "$tmp/fake/scripts/check-ad-blacklist.sh"
  cat > "$tmp/fake/kmp/app/build.gradle.kts" <<'EOF'
plugins { alias(libs.plugins.android.application) }
dependencies {
    // 一条正常依赖，不应命中
    implementation(libs.ktor.client.core)
    implementation("com.bytedance.sdk:openadsdk:6.0.0.0") // 穿山甲，应命中
    implementation("com.sigmob.sdk:windads:4.0.0")       // Sigmob，应命中
}
EOF

  # 用例 1：黑坐标命中 -> 应失败（非 0）
  if bash "$tmp/fake/scripts/check-ad-blacklist.sh" "$tmp/fake" >"$tmp/hit.out" 2>&1; then
    echo "  [FAIL] 用例1：含穿山甲/Sigmob 坐标却判为通过" >&2
    fail=$((fail + 1))
  else
    if grep -qi 'openadsdk' "$tmp/hit.out" && grep -qi 'windads' "$tmp/hit.out"; then
      echo "  [PASS] 用例1：命中穿山甲 + Sigmob 坐标，脚本非零退出并列出命中"
      pass=$((pass + 1))
    else
      echo "  [FAIL] 用例1：脚本失败了但没打印出命中坐标" >&2
      cat "$tmp/hit.out" >&2
      fail=$((fail + 1))
    fi
  fi

  # 用例 2：洁净树（只允许常规依赖 + ad_* 表 + AdSlotProvider + 注释）-> 应通过（0）
  cat > "$tmp/fake/kmp/app/build.gradle.kts" <<'EOF'
plugins { alias(libs.plugins.android.application) }
dependencies {
    // advertising: ad_* 数据模型与 AdSlotProvider 空实现按要求保留，不应命中
    implementation(libs.ktor.client.core)
    implementation(libs.sqldelight.android.driver)
}
EOF
  if bash "$tmp/fake/scripts/check-ad-blacklist.sh" "$tmp/fake" >"$tmp/clean.out" 2>&1; then
    echo "  [PASS] 用例2：洁净依赖声明（含 advertising 注释）判为通过"
    pass=$((pass + 1))
  else
    echo "  [FAIL] 用例2：洁净依赖声明却判为失败" >&2
    cat "$tmp/clean.out" >&2
    fail=$((fail + 1))
  fi

  # 用例 3：真实仓库树应通过（自测从仓库根脚本运行时覆盖当前树）。
  # 不修改真实文件，只调用一次无参检查。
  if bash "$0" >"$tmp/real.out" 2>&1; then
    echo "  [PASS] 用例3：真实仓库树洁净，脚本判为通过"
    pass=$((pass + 1))
  else
    echo "  [FAIL] 用例3：真实仓库树却判为失败" >&2
    cat "$tmp/real.out" >&2
    fail=$((fail + 1))
  fi

  echo "[self-test] 结果：${pass} 通过 / ${fail} 失败"
  return "$fail"
}

# ---------- 主流程 ----------
if (( self_test )); then
  echo "[ad-blacklist] 运行内置自测（临时夹具，不联网）..."
  run_self_test
  exit $?
fi

if [[ ! -d "$kmp_dir" ]]; then
  echo "[ad-blacklist] FAIL：未找到 kmp/ 目录（root=${root}）" >&2
  exit 2
fi

echo "[ad-blacklist] 扫描依赖声明文件中的广告 ADN / ADX 坐标（root=${root}）..."
hits="$(mktemp)" || { echo "[ad-blacklist] FAIL：无法创建临时文件" >&2; exit 2; }
trap 'rm -f -- "$hits"' EXIT
if ! scan_tree "$kmp_dir" >"$hits"; then
  echo "[ad-blacklist] FAIL：扫描过程出错，门禁按失败处理" >&2
  exit 2
fi

if [[ -s "$hits" ]]; then
  echo "[ad-blacklist] FAIL：发现黑名单广告依赖，共 $(wc -l <"$hits") 处：" >&2
  echo "------------------------------------------------------------" >&2
  sed 's/^/  /' "$hits" >&2
  echo "------------------------------------------------------------" >&2
  echo "[ad-blacklist] 设计 §1/§2 要求：不得引入 6 家广告 ADN 或自有 ADX SDK；请移除上述依赖。" >&2
  echo "[ad-blacklist] 结果：FAIL（存在广告依赖）" >&2
  exit 1
fi

echo "[ad-blacklist] PASS：kmp/ 依赖声明中未发现广告 ADN / ADX 坐标。"
echo "[ad-blacklist] 已保留：ad_* 数据模型、AdSlotProvider 空实现、Config/GetV2 广告开关、InterstitialAdActivity no-op alias。"
echo "[ad-blacklist] 结果：PASS（无广告 SDK 依赖）"
exit 0
