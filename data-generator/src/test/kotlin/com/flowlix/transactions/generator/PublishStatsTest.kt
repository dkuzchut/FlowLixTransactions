package com.flowlix.transactions.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PublishStatsTest {
    @Test
    fun `empty snapshot has zero counts and null percentiles`() {
        val stats = PublishStats()
        val snap = stats.snapshot()
        assertEquals(0, snap.successCount)
        assertEquals(0, snap.errorCount)
        assertNull(snap.percentile(50.0))
    }

    @Test
    fun `records errors without affecting success latencies`() {
        val stats = PublishStats()
        stats.recordError()
        stats.recordError()
        val snap = stats.snapshot()
        assertEquals(0, snap.successCount)
        assertEquals(2, snap.errorCount)
        assertNull(snap.percentile(50.0))
    }

    @Test
    fun `percentiles use sorted ack latencies in milliseconds`() {
        val stats = PublishStats()
        // 1ms, 2ms, 3ms, 4ms, 5ms as nanoseconds
        listOf(1L, 5L, 3L, 2L, 4L).forEach { ms -> stats.recordSuccess(ms * 1_000_000) }
        val snap = stats.snapshot()
        assertEquals(5, snap.successCount)
        assertEquals(0, snap.errorCount)
        assertEquals(1.0, snap.percentile(0.0)!!, 0.001)
        assertEquals(3.0, snap.percentile(50.0)!!, 0.001)
        assertEquals(5.0, snap.percentile(100.0)!!, 0.001)
    }

    @Test
    fun `single sample percentile is that sample`() {
        val stats = PublishStats()
        stats.recordSuccess(42L * 1_000_000)
        val snap = stats.snapshot()
        assertEquals(42.0, snap.percentile(50.0)!!, 0.001)
        assertEquals(42.0, snap.percentile(99.0)!!, 0.001)
    }

    @Test
    fun `mixed successes and errors`() {
        val stats = PublishStats()
        stats.recordSuccess(1_000_000)
        stats.recordError()
        stats.recordSuccess(2_000_000)
        stats.recordSuccess(3_000_000)
        val snap = stats.snapshot()
        assertEquals(3, snap.successCount)
        assertEquals(1, snap.errorCount)
        // sorted ms: 1, 2, 3 → p50 index 1 → 2.0
        assertEquals(2.0, snap.percentile(50.0)!!, 0.001)
    }
}
