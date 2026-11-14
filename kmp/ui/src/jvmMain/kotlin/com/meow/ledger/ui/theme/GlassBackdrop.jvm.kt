package com.meow.ledger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * JVM 没有可移植的 backdrop-filter：CMP 1.7.3 桌面端 `RenderEffect` 是 Skia 类型，
 * commonMain 无法构造，且 JVM 仅用于跑 token 单测、不做 UI 渲染验收。
 *
 * 安全退化为不改 Modifier：玻璃填充、描边、投影、sheen 仍由 [glassBackground] 绘制，
 * 只是背后内容不被模糊。绝不在此伪造「通过」的截图 / 模糊效果。
 */
@Composable
actual fun Modifier.glassBackdrop(blurRadiusDp: Int, saturation: Float): Modifier = this
