package com.meow.ledger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 玻璃面背后的「磨砂采样」层：只模糊 / 饱和本层之前绘制的内容，不碰玻璃面自身的
 * 填充、描边、文字与图标（文字、图标、子控件必须保持清晰）。[GlassLayers] 把玻璃面
 * 画在本层之前，业务内容画在本层之后，因此三者都不会互相糊掉。
 *
 * 对应 §2 的 `.glass { backdrop-filter: blur(12px) saturate(1.6) }`。
 * CMP 1.7.3 的 commonMain 没有可移植的 backdrop-filter 构造器，`Modifier.blur`
 * 会把本 composable 的**子内容一起糊掉**（v1 的错误做法），因此这里用平台实现：
 *   - Android 12（API 31）及以上：`graphicsLayer { renderEffect = 12dp 模糊 + 1.6× 饱和 }`；
 *   - Android 11 及以下 / JVM：无 GPU 效应时安全退化为 `Modifier`（玻璃填充、描边、
 *     投影、sheen 仍完整，只是背后内容不被模糊）。
 *
 * 该 Modifier 只应挂在玻璃面**之后**、业务内容**之前**的空 Box 上（不含文字 / 图标），
 * 见 [GlassSurface.kt] 的 `GlassLayers`。不承诺 JVM / 低版本 Android 上有模糊观感，
 * 不做截图测试，也不把退化伪装成通过。
 */
@Composable
expect fun Modifier.glassBackdrop(blurRadiusDp: Int, saturation: Float): Modifier
