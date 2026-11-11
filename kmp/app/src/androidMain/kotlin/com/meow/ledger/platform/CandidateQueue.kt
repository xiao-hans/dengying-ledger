package com.meow.ledger.platform

import java.util.concurrent.ConcurrentLinkedQueue

/** 自动记账候选的进程内队列。持久化与去重在后续自动记账切片接入。 */
data class CaptureCandidate(
    val source: String,
    val sourceApp: String,
    val title: String,
    val text: String,
    val occurredAt: Long,
)

object CandidateQueue {
    private val items = ConcurrentLinkedQueue<CaptureCandidate>()

    fun add(candidate: CaptureCandidate) {
        items.add(candidate)
    }

    fun drain(): List<CaptureCandidate> = buildList {
        while (true) add(items.poll() ?: break)
    }
}
