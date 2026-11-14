package com.meow.ledger.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.sp

/**
 * §2 玻璃构件的参数常量：对应 CSS `.glass { backdrop-filter: blur(12px) saturate(1.6) }`。
 *
 * 【口径】白面够实（`GlassScheme.fill` 浅色 60% 白 / 深色 11% 白）+ 模糊够浅
 * （12dp，背后内容还认得出形状）。模糊 / 饱和只作用于**背板层**（[glassBackdrop]），
 * 玻璃面自身的填充、描边、投影、sheen 与业务内容（文字 / 图标）全部保持清晰——
 * 这正是 v1 把 `Modifier.blur` 挂到内容上导致「字也糊了」的修复点。
 *
 * 【为什么用 drawWithCache 画 sheen】`background(brush)` 只能铺满矩形，无法按 shape
 * 裁切 135° 高光；`drawOutline` 按 shape 描边 + 填充，1px 边线与 sheen 都精确贴合圆角。
 * sheen 峰值 alpha 取 [GlassScheme.sheenAlpha]（浅色 0.30 / 深色 0.06），不是硬编码。
 */
const val GLASS_BLUR_RADIUS_DP = 12
/** §2 CSS `backdrop-filter: saturate(1.6)` 的采样层饱和度倍率，传给 [glassBackdrop]。 */
const val GLASS_SATURATION = 1.6f
/** §2 `.chip.selected` 的半粗字重。 */
val CHIP_SELECTED_WEIGHT = FontWeight.SemiBold

/**
 * 玻璃面的填充 / 描边 / 投影 / sheen 修饰符。**不画背板模糊**——背板模糊由
 * [GlassCard] / [GlassChip] 用独立的 [glassBackdrop] 层提供，避免把内容糊掉。
 *
 * 供 `Box` / `Column` / `Row` 直接 `.glassBackground(...)` 使用，不强制换容器。
 */
@Composable
fun Modifier.glassBackground(
    shape: Shape = RoundedCornerShape(18.dp),
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
            onDrawWithContent {
                drawOutline(outline, brush = SolidColor(fill), style = Fill)
                drawOutline(outline, brush = sheen, style = Fill)
                drawOutline(outline, brush = SolidColor(borderColor), style = Stroke(edgePx))
                drawContent()
            }
        }
}

/**
 * 可复用玻璃卡片：§2 `.glass` 的 Compose 落地。
 * 结构 = 背板磨砂层（[glassBackdrop]）+ 玻璃面（[glassBackground]）+ 内容。
 * 背板层是空 Box，只承载模糊 / 饱和，不含任何文字或图标；内容叠在其上，不被模糊。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    fill: Color = Glass.fill,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .glassBackdrop(GLASS_BLUR_RADIUS_DP, GLASS_SATURATION)
                .clip(shape),
        )
        Box(
            Modifier
                .fillMaxSize()
                .glassBackground(shape = shape, fill = fill),
            content = content,
        )
    }
}

/**
 * 玻璃胶囊：§2 `.chip` / `.chip.selected`。
 * 选中 = 更亮更实的玻璃面（`chipSelectedFill`）+ 半粗主文字；
 * 未选中 = 更透的玻璃面（`chipFill`）+ 次要文字。**绝不用墨色实底**。
 * 填充只画一次（[glassBackground] 内），不再叠加 `background(fill)` 覆盖 sheen 与描边。
 */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    val fill = if (selected) Glass.chipSelectedFill else Glass.chipFill
    val palette = LocalPalette.current
    Box(
        modifier
            .glassBackground(
                shape = shape,
                fill = fill,
                elevation = if (selected) 2.dp else 0.dp,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) palette.textPrimary else palette.textSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) CHIP_SELECTED_WEIGHT else FontWeight.Normal,
        )
    }
}
