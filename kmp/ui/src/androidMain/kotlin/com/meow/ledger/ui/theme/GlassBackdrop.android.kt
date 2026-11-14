package com.meow.ledger.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Android 磨砂采样层：只模糊 / 饱和本层之前绘制的内容，不碰玻璃面与业务内容。
 *
 * API 31+ 用 `android.graphics.RenderEffect` 构造 12dp 模糊 + 1.6× 饱和的 ColorMatrix，
 * 再经 [asComposeRenderEffect] 适配后赋给 `graphicsLayer.renderEffect`——Compose 的
 * `RenderEffect` 是包装类，并非 Android 平台类型的 typealias，不能直接赋值。
 * `graphicsLayer` 以该空 Box 的合成结果为 effect 输入，且强制开启裁切，模糊不会
 * 溢出卡片圆角；`GlassLayers` 把玻璃面画在它之前、业务内容画在它之后，三者互不糊。
 * API 30 及以下没有 RenderEffect，退化为透明层（玻璃填充 / 描边 / 投影 / sheen 仍完整）。
 *
 * 本 Modifier 只挂在不含子内容的采样 Box 上，避免把业务内容糊掉。
 */
@Composable
actual fun Modifier.glassBackdrop(blurRadiusDp: Int, saturation: Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return this.graphicsLayer {
        // graphicsLayer 的 lambda 是 Density 作用域，但入参是 Int(dp)，先转 Dp 才能 toPx()。
        val radiusPx = blurRadiusDp.dp.toPx()
        val blur = AndroidRenderEffect.createBlurEffect(
            radiusPx,
            radiusPx,
            Shader.TileMode.CLAMP,
        )
        // 1.6× 饱和度：ColorMatrix.setSaturation 的入参就是倍率（1f = 原色）。
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val saturate = AndroidRenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(matrix),
        )
        renderEffect = AndroidRenderEffect.createChainEffect(saturate, blur).asComposeRenderEffect()
    }
}
