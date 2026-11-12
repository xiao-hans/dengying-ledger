package com.meow.ledger.ui

import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.network.MeowApi
import com.meow.ledger.shared.network.SyncEngine

/** 进程级依赖入口。app 模块在启动时填充，ui 模块只读。 */
object AppGraph {
    lateinit var database: MeowDatabase

    /** 同步引擎；app 启动时用 [api] 构造。 */
    var sync: SyncEngine? = null

    /** 真实 API（KtorMeowApi）；供后续需要直接发请求的页面/仓储使用。 */
    var api: MeowApi? = null
}
