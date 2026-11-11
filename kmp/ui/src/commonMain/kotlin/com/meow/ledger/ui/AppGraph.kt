package com.meow.ledger.ui

import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.network.SyncEngine

/** 进程级依赖入口。app 模块在启动时填充，ui 模块只读。 */
object AppGraph {
    lateinit var database: MeowDatabase

    /** 同步引擎，纯本地模式为 null；写操作入队时判空。 */
    var sync: SyncEngine? = null
}
