package com.meow.ledger.ui

import com.meow.ledger.shared.db.MeowDatabase

/** 进程级依赖入口。app 模块在启动时填充，ui 模块只读。 */
object AppGraph {
    lateinit var database: MeowDatabase
}
