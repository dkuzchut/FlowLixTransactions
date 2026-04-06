package com.flowlix.transactions.generator

import com.flowlix.transactions.contracts.TransactionSubmittedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class SyntheticTransactionFactoryTest {
    private val fixedInstant = Instant.parse("2026-04-04T12:00:00Z")
    private val event =
        TransactionSubmittedEvent(
            externalId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            merchantId = "merchant-a",
            amountMinor = 99,
            currency = "EUR",
            createdAt = fixedInstant,
        )

    @Test
    fun `partition key EXTERNAL_ID is string form of id`() {
        assertEquals(
            "11111111-1111-1111-1111-111111111111",
            SyntheticTransactionFactory.partitionKey(
                event,
                PartitionKeying.EXTERNAL_ID,
                sequence = 0L,
                partitions = 3,
            ),
        )
    }

    @Test
    fun `partition key MERCHANT is merchant id`() {
        assertEquals(
            "merchant-a",
            SyntheticTransactionFactory.partitionKey(
                event,
                PartitionKeying.MERCHANT,
                sequence = 0L,
                partitions = 3,
            ),
        )
    }

    @Test
    fun `partition key ROUND_ROBIN cycles by sequence and partitions`() {
        assertEquals(
            "0",
            SyntheticTransactionFactory.partitionKey(
                event,
                PartitionKeying.ROUND_ROBIN,
                sequence = 0L,
                partitions = 3,
            ),
        )
        assertEquals(
            "1",
            SyntheticTransactionFactory.partitionKey(
                event,
                PartitionKeying.ROUND_ROBIN,
                sequence = 1L,
                partitions = 3,
            ),
        )
        assertEquals(
            "2",
            SyntheticTransactionFactory.partitionKey(
                event,
                PartitionKeying.ROUND_ROBIN,
                sequence = 2L,
                partitions = 3,
            ),
        )
        assertEquals(
            "0",
            SyntheticTransactionFactory.partitionKey(
                event,
                PartitionKeying.ROUND_ROBIN,
                sequence = 3L,
                partitions = 3,
            ),
        )
        assertEquals(
            "0",
            SyntheticTransactionFactory.partitionKey(
                event,
                PartitionKeying.ROUND_ROBIN,
                sequence = -3L,
                partitions = 3,
            ),
        )
    }

    @Test
    fun `next produces plausible fields with seeded random`() {
        val random = Random(0L)
        val tx = SyntheticTransactionFactory.next(random)
        assertTrue(tx.amountMinor in 50 until 500_000)
        assertTrue(tx.currency in setOf("EUR", "USD", "GBP", "CHF"))
        assertTrue(tx.merchantId.isNotEmpty())
        assertTrue(tx.externalId.toString().isNotEmpty())
    }

    @Test
    fun `next with same seed yields same merchant amount and currency`() {
        val a = SyntheticTransactionFactory.next(Random(42L))
        val b = SyntheticTransactionFactory.next(Random(42L))
        assertEquals(a.merchantId, b.merchantId)
        assertEquals(a.amountMinor, b.amountMinor)
        assertEquals(a.currency, b.currency)
    }
}
