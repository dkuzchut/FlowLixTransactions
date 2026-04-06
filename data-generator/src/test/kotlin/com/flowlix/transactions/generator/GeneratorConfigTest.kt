package com.flowlix.transactions.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GeneratorConfigTest {
    @Test
    fun `publish config computes total messages`() {
        val c =
            PublishConfig(
                ratePerSecond = 500,
                durationSeconds = 10,
                bootstrapServers = "localhost:19092",
                topic = "transaction-submitted",
                partitionKeying = PartitionKeying.ROUND_ROBIN,
                partitions = 6,
                merchantId = "merchant-x",
            )
        assertEquals(5000L, c.totalMessages)
    }

    @Test
    fun `generator config validates partitions`() {
        assertThrows<IllegalArgumentException> {
            GeneratorConfig(
                bootstrapServers = "localhost:19092",
                topic = "transaction-submitted",
                partitionKeying = PartitionKeying.EXTERNAL_ID,
                partitions = 0,
            )
        }
    }

    @Test
    fun `publish config rejects invalid rate`() {
        assertThrows<IllegalArgumentException> {
            PublishConfig(
                ratePerSecond = 0,
                durationSeconds = 1,
                bootstrapServers = "localhost:19092",
                topic = "transaction-submitted",
                partitionKeying = PartitionKeying.EXTERNAL_ID,
                partitions = 1,
            )
        }
    }
}
