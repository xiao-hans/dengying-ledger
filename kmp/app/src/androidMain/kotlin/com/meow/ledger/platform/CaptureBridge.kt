package com.meow.ledger.platform

import com.meow.ledger.ui.AppGraph
import com.meow.ledger.shared.auto.AutoRepository
import com.meow.ledger.shared.auto.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 把三路采集服务的候选交给待确认队列。
 * - 只处理监听白名单里的包名；
 * - 通知用包名，无障碍与截屏来源只保留文本；
 * - 每个候选落库后立即从内存队列移除，进程被杀最多丢当前一条。
 */
object CaptureBridge {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            val settings = SettingsStore(AppGraph.database)
            val repo = AutoRepository(AppGraph.database)
            while (isActive) {
                val batch = CandidateQueue.drain()
                if (batch.isEmpty()) {
                    delay(500)
                    continue
                }
                val watch = settings.watchApps()
                for (candidate in batch) {
                    val allowed = candidate.sourceApp.isEmpty() || candidate.sourceApp in watch
                    if (!allowed) continue
                    val raw = buildString {
                        if (candidate.title.isNotBlank()) append(candidate.title).append('\n')
                        append(candidate.text)
                    }
                    runCatching { repo.capture(raw, candidate.source, candidate.occurredAt, candidate.sourceApp) }
                }
            }
        }
    }
}
