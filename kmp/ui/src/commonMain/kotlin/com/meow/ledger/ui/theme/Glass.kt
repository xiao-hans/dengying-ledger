package com.meow.ledger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 灯影玻璃层 token（`docs/风格对照.md` §2，v5 磨砂）——悬浮玻璃控件的唯一取值来源。
 *
 * 【与 [PaletteScheme] 的关系】配色由 `Color.kt` 的 `PaletteScheme` 管页面底色 / 文字 /
 * 分隔线；玻璃质感全部由本文件管。为避免两份真相，`Color.kt` 里原有的
 * `glassFill` / `glassBorder` / `glassShadow` / `chipFill` / `chipSelectedFill` /
 * `dockHighlight` 六个字段改为**委托只读属性**，转发到 `PaletteScheme.glass`
 * （即 [LightGlass] / [DarkGlass]），本文件仍是取值的唯一定义处。
 *
 * 【v5 口径：白面够实 + 模糊够浅】浅色 `fill` 为 60% 白，深色为 11% 白；
 * `GlassSurface` 用 12dp 模糊 + 1.6× 饱和度让背后内容还认得出形状，
 * 既不像塑料膜（太透）也不退回白卡（太实）。
 *
 * 【选中态不用墨色实底】选中 = 更亮更实的玻璃面 + 半粗文字；未选中 = 更透的玻璃面 +
 * 次要文字。深浅色都用白 / 黑透明度反向表达，见 [LightGlass] / [DarkGlass]；
 * `fillStrong` 在深色下也用白色半透明（不是浅色那块近实白），避免 Dock 变成亮斑。
 *
 * 【主题感知】与 `LocalPalette` 同机制：[LocalGlassScheme] 由 `AppTheme.kt` 的
 * [MeowTheme]（内部按三态计算 `glassOf(theme)`）注入，[Glass] 的 getter 读
 * CompositionLocal，调用点零改动。缺省值 [LightGlass] 只用于没有主题上下文的预览 / 测试。
 *
 * §2 表格之外的 DengYing 私有字段（`actionFill` / `actionText`）不是本工程所需——
 * 主按钮沿用 `PaletteScheme.brand`，故未移植，避免引入无人使用的 token。
 */
data class GlassScheme(
    /** 玻璃面常态填充：`.glass`（`blur(12px) saturate(1.6)`） */
    val fill: Color,
    /** 强化玻璃：`.glass-strong`（Dock / 对话框 / 悬浮宠物） */
    val fillStrong: Color,
    /** 暗色玻璃：`.glass-dark`（Toast），深浅色同值：视频 / 暗底上永远是暗玻璃 */
    val darkFill: Color,
    /** 液态染色：备用 token（液态高光层），保留语义不参与静态填充 */
    val liquidTint: Color,
    /** `.glass` 1px 描边 */
    val border: Color,
    /** `.glass` / `.card` / `.dock-plus` 投影色 */
    val shadow: Color,
    /** 胶囊 chip 未选中填充 */
    val chipFill: Color,
    /** 胶囊 chip 选中填充（更亮更实，非墨色），配合 `FontWeight.SemiBold` */
    val chipSelectedFill: Color,
    /** Dock 选中项背后的滑动高光面板 */
    val dockHighlight: Color,
    /** `.glass::after` 135° sheen 峰值 alpha：浅色 0.30 / 深色 0.06 */
    val sheenAlpha: Float,
)

val LightGlass = GlassScheme(
    fill = Color(0x99FFFFFF),
    fillStrong = Color(0xC7FFFFFF),
    darkFill = Color(0x73000000),
    liquidTint = Color(0x66FFFFFF),
    border = Color(0x240C0C09),
    shadow = Color(0x1A0C0C09),
    chipFill = Color(0x5CFFFFFF),
    chipSelectedFill = Color(0xCCFFFFFF),
    dockHighlight = Color(0xD9FFFFFF),
    sheenAlpha = 0.30f,
)

val DarkGlass = GlassScheme(
    fill = Color(0x1CFFFFFF),
    fillStrong = Color(0x3DFFFFFF),
    darkFill = Color(0x73000000),
    liquidTint = Color(0x1CFFFFFF),
    border = Color(0x3DFFFFFF),
    shadow = Color(0x66000000),
    chipFill = Color(0x2EFFFFFF),
    chipSelectedFill = Color(0x52FFFFFF),
    dockHighlight = Color(0x3DFFFFFF),
    sheenAlpha = 0.06f,
)

/** 当前主题的玻璃方案，由 `AppTheme.kt` 的 [MeowTheme] 注入（内部用 `glassOf(theme)`）。 */
val LocalGlassScheme = staticCompositionLocalOf { LightGlass }

/** 兼容层：@Composable getter 读 CompositionLocal，调用点零改动。 */
object Glass {
    val fill: Color @Composable get() = LocalGlassScheme.current.fill
    val fillStrong: Color @Composable get() = LocalGlassScheme.current.fillStrong
    val darkFill: Color @Composable get() = LocalGlassScheme.current.darkFill
    val liquidTint: Color @Composable get() = LocalGlassScheme.current.liquidTint
    val border: Color @Composable get() = LocalGlassScheme.current.border
    val shadow: Color @Composable get() = LocalGlassScheme.current.shadow
    val chipFill: Color @Composable get() = LocalGlassScheme.current.chipFill
    val chipSelectedFill: Color @Composable get() = LocalGlassScheme.current.chipSelectedFill
    val dockHighlight: Color @Composable get() = LocalGlassScheme.current.dockHighlight
    val sheenAlpha: Float @Composable get() = LocalGlassScheme.current.sheenAlpha
}
