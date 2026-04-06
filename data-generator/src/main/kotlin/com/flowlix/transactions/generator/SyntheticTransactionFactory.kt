package com.flowlix.transactions.generator

import com.flowlix.transactions.contracts.TransactionSubmittedEvent
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

private val CURRENCIES = arrayOf("EUR", "USD", "GBP", "CHF")

object SyntheticTransactionFactory {
    private val merchantPool: List<String> = List(256) { UUID.randomUUID().toString() }

    fun next(random: Random = Random.Default, merchantIdOverride: String? = null): TransactionSubmittedEvent {
        val externalId = UUID.randomUUID()
        return TransactionSubmittedEvent(
            externalId = externalId,
            merchantId = merchantIdOverride ?: merchantPool[random.nextInt(merchantPool.size)],
            amountMinor = random.nextLong(50, 500_000),
            currency = CURRENCIES[random.nextInt(CURRENCIES.size)],
            createdAt = Instant.now(),
        )
    }

    fun partitionKey(
        event: TransactionSubmittedEvent,
        keying: PartitionKeying,
        sequence: Long,
        partitions: Int,
    ): String? =
        when (keying) {
            PartitionKeying.EXTERNAL_ID -> event.externalId.toString()
            PartitionKeying.MERCHANT -> event.merchantId
            PartitionKeying.ROUND_ROBIN -> (sequence % partitions).toString()
        }
}
