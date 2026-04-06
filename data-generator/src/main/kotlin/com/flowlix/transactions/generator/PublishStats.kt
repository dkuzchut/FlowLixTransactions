package com.flowlix.transactions.generator

class PublishStats {
    private val lock = Any()
    private val latencies = ArrayList<Long>()
    private var errorCount = 0
    private var maxScheduleLagNanos: Long = 0
    private var totalScheduleLagNanos: Long = 0
    private var scheduleLagSamples: Long = 0

    fun recordSuccess(latencyNanos: Long) {
        synchronized(lock) {
            latencies.add(latencyNanos)
        }
    }

    fun recordError() {
        synchronized(lock) {
            errorCount++
        }
    }

    fun recordScheduleLag(lagNanos: Long) {
        if (lagNanos <= 0) return
        synchronized(lock) {
            scheduleLagSamples++
            totalScheduleLagNanos += lagNanos
            if (lagNanos > maxScheduleLagNanos) maxScheduleLagNanos = lagNanos
        }
    }

    fun snapshot(): PublishStatsSnapshot {
        val copy: LongArray
        val errs: Int
        val maxLag: Long
        val totalLag: Long
        val lagSamples: Long
        synchronized(lock) {
            copy = latencies.toLongArray()
            errs = errorCount
            maxLag = maxScheduleLagNanos
            totalLag = totalScheduleLagNanos
            lagSamples = scheduleLagSamples
        }
        copy.sort()
        return PublishStatsSnapshot(
            successCount = copy.size,
            errorCount = errs,
            sortedLatencyMillis = copy.map { it / 1_000_000.0 }.toDoubleArray(),
            maxScheduleLagMillis = maxLag / 1_000_000.0,
            avgScheduleLagMillis = if (lagSamples == 0L) 0.0 else (totalLag / 1_000_000.0) / lagSamples.toDouble(),
        )
    }
}

data class PublishStatsSnapshot(
    val successCount: Int,
    val errorCount: Int,
    val sortedLatencyMillis: DoubleArray,
    val maxScheduleLagMillis: Double,
    val avgScheduleLagMillis: Double,
) {
    fun percentile(percent: Double): Double? {
        if (sortedLatencyMillis.isEmpty()) return null
        val idx = ((percent / 100.0) * (sortedLatencyMillis.size - 1)).toInt()
            .coerceIn(0, sortedLatencyMillis.size - 1)
        return sortedLatencyMillis[idx]
    }
}
