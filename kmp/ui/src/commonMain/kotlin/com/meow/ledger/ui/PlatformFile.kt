package com.meow.ledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 平台文件桥。
 * - ImportCsvButton：选一个 CSV/文本文件，把名字和内容交给 onPicked。
 * - shareTextFile：把文本写成文件并发起系统分享，返回是否发起成功。
 * OCR 引擎未接入时 platformOcrName 为空，页面只提供手动与粘贴入口。
 */
@Composable
expect fun ImportCsvButton(enabled: Boolean, modifier: Modifier = Modifier, onPicked: (name: String, content: String) -> Unit)

expect fun shareTextFile(name: String, content: String): Boolean

expect val platformOcrName: String?

/** 选一张票据图，拷贝到本地缓存后把路径交给 onPicked。平台没有选图能力时按钮禁用。 */
@Composable
expect fun PickImageButton(enabled: Boolean, modifier: Modifier = Modifier, onPicked: (path: String) -> Unit)
