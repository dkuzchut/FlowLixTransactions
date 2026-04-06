package com.flowlix.transactions.generator

enum class PartitionKeying {
    /** Hash by Kafka on full external id string — spreads with default partitioner. */
    EXTERNAL_ID,

    /** Same merchant tends to land on one partition (ordering per merchant). */
    MERCHANT,

    /** Cycle numeric keys `0..partitions-1` so load spreads when partition count matches the topic. */
    ROUND_ROBIN,
}

data class GeneratorConfig(
    val bootstrapServers: String,
    val topic: String,
    val partitionKeying: PartitionKeying,
    /** Used with [PartitionKeying.ROUND_ROBIN]; should match the topic partition count for even spread. */
    val partitions: Int,
) {
    init {
        require(partitions > 0) { "PARTITIONS must be > 0" }
    }
}

data class PublishConfig(
    val ratePerSecond: Int,
    val durationSeconds: Int,
    val bootstrapServers: String,
    val topic: String,
    val partitionKeying: PartitionKeying,
    val partitions: Int,
    val merchantId: String? = null,
) {
    init {
        require(ratePerSecond > 0) { "rate must be > 0" }
        require(durationSeconds > 0) { "duration must be > 0" }
        require(partitions > 0) { "partitions must be > 0" }
    }

    val totalMessages: Long
        get() = ratePerSecond.toLong() * durationSeconds
}

fun loadGeneratorConfigFromEnv(): GeneratorConfig {
    return GeneratorConfig(
        bootstrapServers = bootstrapServers,
        topic = topic,
        partitionKeying = partitionKeying,
        partitions = partitions,
    )
}

private val DEFAULT_BOOTSTRAP = "localhost:19092"
private val DEFAULT_TOPIC = "transaction-submitted"

private val bootstrapServers: String
    get() = System.getenv("BOOTSTRAP_SERVERS") ?: DEFAULT_BOOTSTRAP

private val topic: String
    get() = System.getenv("TOPIC") ?: DEFAULT_TOPIC

private val partitions: Int
    get() = System.getenv("PARTITIONS")?.toIntOrNull() ?: 1

private val partitionKeying: PartitionKeying
    get() = System.getenv("KEYING")?.let { PartitionKeying.valueOf(it.uppercase()) } ?: PartitionKeying.EXTERNAL_ID

val defaultRatePerSecond: Int
    get() = System.getenv("RATE")?.toIntOrNull() ?: 1000

val defaultDurationSeconds: Int
    get() = System.getenv("DURATION")?.toIntOrNull() ?: 60
