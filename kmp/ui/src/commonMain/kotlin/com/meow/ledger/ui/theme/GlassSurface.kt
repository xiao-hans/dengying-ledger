package com.meow.ledger.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * §2 玻璃构件的参数常量：对应 CSS `.glass { backdrop-filter: blur(12px) saturate(1.6) }`。
 *
 * 【口径】白面够实（`GlassScheme.fill` 浅色 60% 白 / 深色 11% 白）+ 模糊够浅
 * （12dp，背后内容还认得出形状）。[GlassLayers] 把玻璃面画在前、把只含
 * [glassBackdrop] 的空采样层夹在玻璃面与业务内容之间：模糊 / 饱和只作用于采样层，
 * 玻璃面自身的填充、描边、投影、sheen 与业务内容（文字 / 图标）全部保持清晰——
 * 这正是 v1 把 `Modifier.blur` 挂到内容上导致「字也糊了」的修复点。
 *
 * 【为什么用 drawWithCache 画 sheen】`background(brush)` 只能铺满矩形，无法按 shape
 * 裁切 135° 高光；`drawOutline` 按 shape 描边 + 填充，1px 边线与 sheen 都精确贴合圆角。
 * sheen 峰值 alpha 取 [GlassScheme.sheenAlpha]（浅色 0.30 / 深色 0.06），不是硬编码。
 *
 * 【API 26–30 的背板】低版本没有 GPU 模糊，采样层退化为透明（见 [glassBackdrop]）；
 * 玻璃面仍绘制半透明填充 / 描边 / 投影 / sheen，绝不把文字糊掉。
 */
const val GLASS_BLUR_RADIUS_DP = 12
/** §2 CSS `backdrop-filter: saturate(1.6)` 的采样层饱和度倍率，传给 [glassBackdrop]。 */
const val GLASS_SATURATION = 1.6f
/** §4 卡片圆角。 */
val GLASS_CARD_SHAPE: Shape = RoundedCornerShape(18.dp)
/** §4 胶囊圆角：全圆。 */
val GLASS_PILL_SHAPE: Shape = RoundedCornerShape(999.dp)
/** §2 `.chip.selected` 的半粗字重。 */
val CHIP_SELECTED_WEIGHT = FontWeight.SemiBold

/**
 * 玻璃面的填充 / 描边 / 投影 / sheen 修饰符。**不画背板模糊**——按 shape 裁切后，
 * 在 `drawContent()` 之前铺底并描边；[GlassLayers] 会把独立的 [glassBackdrop]
 * 采样层作为该 composable 的内容之一放在业务内容之前，避免把业务内容糊掉。
 *
 * 供 `Box` / `Column` / `Row` 直接 `.glassBackground(...)` 使用，不强制换容器。
 */
@Composable
fun Modifier.glassBackground(
    shape: Shape = GLASS_CARD_SHAPE,
    fill: Color = Glass.fill,
    borderColor: Color = Glass.border,
    shadowColor: Color = Glass.shadow,
    sheenAlpha: Float = Glass.sheenAlpha,
    elevation: Dp = 6.dp,
): Modifier {
    val edge = 1.dp
    return this
        .shadow(elevation, shape, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val edgePx = edge.toPx()
            val sheen = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = sheenAlpha),
                    Color.Transparent,
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height * 1.4f),
            )
            onDrawBehind {
                drawOutline(outline, brush = SolidColor(fill), style = Fill)
                drawOutline(outline, brush = sheen, style = Fill)
                drawOutline(outline, brush = SolidColor(borderColor), style = Stroke(edgePx))
            }
        }
}

/**
 * 玻璃卡片便捷修饰器：给已有的 `Row` / `Column` / `Box` 直接换上玻璃面
 * （填充 / 描边 / 投影 / sheen），替代散落各页的
 * `shadow + clip + background(cardBg) + border(divider)` 白卡四件套。
 *
 * 注意：Modifier 无法插入「兄弟背板层」，故本修饰器只提供玻璃面；
 * 完整三层（玻璃面 → [glassBackdrop] 采样层 → 内容）请用 [GlassPanel] / [GlassCard] /
 * [GlassChip]。列表卡与 Dock 等悬浮件走三层版本，内嵌在滚动内容里的普通卡用本
 * 便捷修饰器即可；都取同一套 [GlassScheme] token，不会出现「一半白卡一半玻璃」。
 */
@Composable
fun Modifier.glassCard(
    shape: Shape = GLASS_CARD_SHAPE,
    fill: Color = Glass.fill,
    elevation: Dp = 2.dp,
): Modifier = glassBackground(
    shape = shape,
    fill = fill,
    elevation = elevation,
)

/**
 * 玻璃构件的统一结构：**玻璃面 → 空的模糊采样层 → 内容**。
 *
 * 外层 Box 的 `drawBehind` 先绘制填充 / 描边 / 投影 / sheen；紧随其后的空 Box 用
 * `matchParentSize` + [glassBackdrop] 对**已绘制的玻璃面背后的区域**做模糊 / 饱和采样
 * （`matchParentSize` 是 `BoxScope` 的成员扩展，在 Box 内容 lambda 内直接调用、无需 import；
 * 它在其它子内容测量完后按父级最终尺寸测量，因此滚动列表里不会出现 0 高度的采样层；
 * API 31+ 的 `graphicsLayer` 会强制裁切，模糊不会溢出圆角），业务内容最后绘制。
 * 因此玻璃面与文字 / 图标都不被模糊，外层高度由内容撑开。所有玻璃组件
 * （[GlassPanel] / [GlassCard] / [GlassChip]）都复用本函数，避免两套观感。
 */
@Composable
private fun GlassLayers(
    modifier: Modifier,
    shape: Shape,
    fill: Color,
    elevation: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glassBackground(shape = shape, fill = fill, elevation = elevation)) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .glassBackdrop(GLASS_BLUR_RADIUS_DP, GLASS_SATURATION),
        )
        content()
    }
}

/**
 * 列表 / 表单用的玻璃面板：§2 `.glass` 的 Compose 落地，内容按 [ColumnScope] 排布。
 *
 * `onClick` 为空时不可点；`contentPadding` 默认 14dp，与旧白卡一致，调用点零改动。
 * 这是 `manage` 包各页面「GlassCard」的唯一实现来源（见 `Plans.kt` 的委托）。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GLASS_CARD_SHAPE,
    fill: Color = Glass.fill,
    elevation: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassLayers(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        fill = fill,
        elevation = elevation,
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/** 兼容旧调用：Box 内容的玻璃卡片，内部委托 [GlassLayers]。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GLASS_CARD_SHAPE,
    fill: Color = Glass.fill,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassLayers(modifier = modifier, shape = shape, fill = fill, elevation = 6.dp, content = content)
}

/**
 * 玻璃胶囊：§2 `.chip` / `.chip.selected`。
 * 选中 = 更亮更实的玻璃面（`chipSelectedFill`）+ 半粗主文字；
 * 未选中 = 更透的玻璃面（`chipFill`）+ 次要文字。**绝不用墨色实底**。
 * 填充只画一次（[glassBackground] 内），不再叠加 `background(fill)` 覆盖 sheen 与描边。
 *
 * 【胶囊按文字宽度收缩】内容 Box 不挂 `fillMaxWidth()`：外层 [GlassLayers] 会收到
 * Row / Column 给的可用宽度上界，若填满，Row 里第一颗 chip 会吃掉整行剩余宽度、
 * Column 里的 chip 会变成通栏，破坏灯影的横向胶囊排布。外层 Box 默认不向子级传递
 * 最小宽度约束，去掉它后胶囊就是「文字 + 18dp 左右内边距」，点击区仍是整颗胶囊；
 * 需要定宽时由调用方显式传 `modifier`。
 */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = if (selected) Glass.chipSelectedFill else Glass.chipFill
    val palette = LocalPalette.current
    GlassLayers(
        modifier = modifier,
        shape = GLASS_PILL_SHAPE,
        fill = fill,
        elevation = if (selected) 2.dp else 0.dp,
    ) {
        Box(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (selected) palette.textPrimary else palette.textSecondary,
                style = if (selected) AppTypography.bodyMedium.semiBold() else AppTypography.bodyMedium,
            )
        }
    }
}
