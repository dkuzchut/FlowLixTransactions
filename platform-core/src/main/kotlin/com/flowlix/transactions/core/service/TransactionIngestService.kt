package com.flowlix.transactions.core.service

import com.flowlix.transactions.contracts.TransactionSubmittedEvent
import com.flowlix.transactions.core.repository.TransactionRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

@Service
class TransactionIngestService(
    private val transactionRepository: TransactionRepository,
    meterRegistry: MeterRegistry,
) {
    private val ingestedCounter: Counter = Counter.builder("platform_core_transactions_ingested_total").register(meterRegistry)
    private val duplicateCounter: Counter = Counter.builder("platform_core_transactions_duplicates_total").register(meterRegistry)

    fun ingest(event: TransactionSubmittedEvent) {
        val inserted = transactionRepository.insertInProgressIfAbsent(event)
        if (inserted) {
            ingestedCounter.increment()
        } else {
            duplicateCounter.increment()
        }
    }
}
