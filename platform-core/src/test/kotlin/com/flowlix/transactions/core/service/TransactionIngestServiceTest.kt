package com.flowlix.transactions.core.service

import com.flowlix.transactions.contracts.TransactionSubmittedEvent
import com.flowlix.transactions.core.repository.TransactionRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TransactionIngestServiceTest {
    private val transactionRepository = mockk<TransactionRepository>()
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var service: TransactionIngestService

    private val fixedNow = Instant.parse("2026-04-05T09:00:00Z")
    private val event =
        TransactionSubmittedEvent(
            externalId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            merchantId = "merchant-42",
            amountMinor = 1_000L,
            currency = "EUR",
            createdAt = fixedNow,
        )

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        service = TransactionIngestService(transactionRepository, meterRegistry)
    }

    @AfterEach
    fun tearDown() {
        clearMocks(transactionRepository)
    }

    @Test
    fun `ingest records new transactions and is immediately due`() {
        every { transactionRepository.insertInProgressIfAbsent(event) } returns true

        service.ingest(event)

        assertEquals(1.0, meterRegistry.counter("platform_core_transactions_ingested_total").count())
        assertEquals(0.0, meterRegistry.counter("platform_core_transactions_duplicates_total").count())

        verify(exactly = 1) { transactionRepository.insertInProgressIfAbsent(event) }
    }

    @Test
    fun `ingest increments duplicate counter when insert skipped`() {
        every { transactionRepository.insertInProgressIfAbsent(event) } returns false

        service.ingest(event)

        assertEquals(0.0, meterRegistry.counter("platform_core_transactions_ingested_total").count())
        assertEquals(1.0, meterRegistry.counter("platform_core_transactions_duplicates_total").count())

        verify(exactly = 1) { transactionRepository.insertInProgressIfAbsent(event) }
    }
}
