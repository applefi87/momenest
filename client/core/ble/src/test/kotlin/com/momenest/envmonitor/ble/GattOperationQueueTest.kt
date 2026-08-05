/*
 * GattOperationQueueTest.kt
 *
 * 序列化失效的症狀是偶發的：多數時候操作剛好錯開就沒事，偶爾撞在一起就整條
 * 連線卡死。這種 bug 靠實機測幾乎抓不到，所以在這裡用「刻意製造重疊」的方式驗證。
 */
package com.momenest.envmonitor.ble

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.test.assertFailsWith

class GattOperationQueueTest {

    @Test
    fun `同一時間只有一個操作在執行`() = runTest {
        val queue = GattOperationQueue()
        var running = 0
        var maxConcurrent = 0

        val jobs = (1..50).map {
            async {
                queue.execute {
                    running++
                    maxConcurrent = maxOf(maxConcurrent, running)
                    // 讓出執行權：若沒有序列化，其他協程會在這裡插隊進來
                    yield()
                    yield()
                    running--
                }
            }
        }
        jobs.awaitAll()

        assertThat(maxConcurrent).isEqualTo(1)
        assertThat(running).isEqualTo(0)
    }

    @Test
    fun `操作依抵達順序執行`() = runTest {
        val queue = GattOperationQueue()
        val order = mutableListOf<Int>()

        // 第一個操作卡住，確保後面三個一定是排隊等待的狀態
        val gate = CompletableDeferred<Unit>()
        val first = launch { queue.execute { gate.await(); order.add(0) } }
        yield()

        val rest = (1..3).map { index ->
            launch { queue.execute { order.add(index) } }.also { yield() }
        }

        gate.complete(Unit)
        first.join()
        rest.forEach { it.join() }

        assertThat(order).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `回傳值會原樣傳回呼叫端`() = runTest {
        val queue = GattOperationQueue()
        assertThat(queue.execute { "ok" }).isEqualTo("ok")
        assertThat(queue.execute { 42 }).isEqualTo(42)
        assertThat(queue.execute { null }).isNull()
    }

    @Test
    fun `操作擲例外時例外會往外傳且不吞掉`() = runTest {
        val queue = GattOperationQueue()
        val error = assertFailsWith<IllegalStateException> {
            queue.execute { throw IllegalStateException("GATT 忙碌") }
        }
        assertThat(error).hasMessageThat().isEqualTo("GATT 忙碌")
    }

    @Test
    fun `操作擲例外後佇列仍可繼續服務`() = runTest {
        // 這是重點：Mutex 沒正確釋放的話，一次失敗就會讓整條連線永久卡死
        val queue = GattOperationQueue()
        runCatching { queue.execute { throw RuntimeException("boom") } }

        assertThat(queue.execute { "後續操作正常" }).isEqualTo("後續操作正常")
    }

    @Test
    fun `等待中的操作被取消後佇列仍可繼續服務`() = runTest {
        val queue = GattOperationQueue()
        val gate = CompletableDeferred<Unit>()

        val holder = launch { queue.execute { gate.await() } }
        yield()
        val waiting = launch { queue.execute { /* 永遠等不到鎖就被取消 */ } }
        yield()

        waiting.cancel()
        gate.complete(Unit)
        holder.join()

        assertThat(queue.execute { "仍可用" }).isEqualTo("仍可用")
    }
}
