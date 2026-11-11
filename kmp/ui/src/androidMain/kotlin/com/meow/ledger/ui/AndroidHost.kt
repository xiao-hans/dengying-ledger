package com.meow.ledger.ui

import android.content.Context

/** app 模块在启动时填充，ui 模块的文件桥只读。 */
object AndroidHost {
    lateinit var appContext: Context
}
