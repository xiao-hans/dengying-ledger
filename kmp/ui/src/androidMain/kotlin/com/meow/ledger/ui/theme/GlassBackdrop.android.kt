package com.meow.ledger.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Android 背板磨砂：只模糊 / 饱和本层背后的内容，不碰玻璃面自身的文字与图标。
 *
 * API 31+ 用 `android.graphics.RenderEffect`（Compose 的 `RenderEffect` 在 Android
 * 上就是它的 typealias）：12dp 模糊叠加 1.6× 饱和的 ColorMatrix，链成单个 effect。
 * API 30 及以下没有 RenderEffect，退化为透明层（玻璃填充 / 描边 / 投影 / sheen 仍完整）。
 *
 * 本 Modifier 只挂在不含子内容的背板 Box 上，避免把业务内容糊掉。
 */
@Composable
actual fun Modifier.glassBackdrop(blurRadiusDp: Int, saturation: Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return this.graphicsLayer {
        val blur = AndroidRenderEffect.createBlurEffect(
            blurRadiusDp.toPx(),
            blurRadiusDp.toPx(),
            Shader.TileMode.CLAMP,
        )
        // 1.6× 饱和度：ColorMatrix.setSaturation 的入参就是倍率（1f = 原色）。
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val saturate = AndroidRenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(matrix),
        )
        renderEffect = AndroidRenderEffect.createChainEffect(saturate, blur)
    }
}
