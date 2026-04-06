package com.flowlix.transactions.core.service

import com.flowlix.transactions.core.config.PlatformCoreProperties
import com.flowlix.transactions.core.domain.TransactionStatus
import com.flowlix.transactions.core.repository.TransactionRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class TransactionFinalizationService(
    private val transactionRepository: TransactionRepository,
    private val decisionService: TransactionDecisionService,
    private val properties: PlatformCoreProperties,
    meterRegistry: MeterRegistry,
    private val clock: Clock,
) {
    private val succeededCounter: Counter =
        Counter.builder("platform_core_transactions_finalized_succeeded_total").register(meterRegistry)
    private val failedCounter: Counter =
        Counter.builder("platform_core_transactions_finalized_failed_total").register(meterRegistry)
    private val endToEndLatencyTimer: Timer =
        Timer.builder("platform_core_transactions_end_to_end_latency").register(meterRegistry)
    private val finalizerRunTimer: Timer =
        Timer.builder("platform_core_finalizer_run_duration").register(meterRegistry)
    private val claimedBatchSummary: DistributionSummary =
        DistributionSummary.builder("platform_core_finalizer_claimed_batch_size").register(meterRegistry)
    private val updatedBatchSummary: DistributionSummary =
        DistributionSummary.builder("platform_core_finalizer_updated_batch_size").register(meterRegistry)

    @Scheduled(fixedDelayString = "\${platform-core.finalizer-fixed-delay-ms:75}")
    @Transactional
    fun finalizeDueTransactions() {
        finalizerRunTimer.record(
            Runnable {
            val dueTransactions = transactionRepository.claimReadyForFinalization(properties.finalizeBatchSize)
            claimedBatchSummary.record(dueTransactions.size.toDouble())
            val now = Instant.now(clock)

            val succeededIds = mutableListOf<java.util.UUID>()
            val failedIds = mutableListOf<java.util.UUID>()
            val createdAtById = mutableMapOf<java.util.UUID, Instant>()

            dueTransactions.forEach { tx ->
                createdAtById[tx.id] = tx.createdAt
                when (decisionService.decideFinalStatus(tx.externalId)) {
                    TransactionStatus.SUCCEEDED -> succeededIds.add(tx.id)
                    TransactionStatus.FAILED -> failedIds.add(tx.id)
                    else -> Unit
                }
            }

            val succeededUpdated = transactionRepository.markFinalizedBatch(succeededIds, TransactionStatus.SUCCEEDED, null)
            val failedUpdated =
                transactionRepository.markFinalizedBatch(failedIds, TransactionStatus.FAILED, "deterministic-failure")
            updatedBatchSummary.record((succeededUpdated + failedUpdated).toDouble())

            succeededCounter.increment(succeededUpdated.toDouble())
            failedCounter.increment(failedUpdated.toDouble())

            if (succeededUpdated == succeededIds.size) {
                succeededIds.forEach { id ->
                    val createdAt = createdAtById[id] ?: return@forEach
                    endToEndLatencyTimer.record(Duration.between(createdAt, now))
                }
            }
            if (failedUpdated == failedIds.size) {
                failedIds.forEach { id ->
                    val createdAt = createdAtById[id] ?: return@forEach
                    endToEndLatencyTimer.record(Duration.between(createdAt, now))
                }
            }
            },
        )
    }
}
