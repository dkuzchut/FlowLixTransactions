package com.flowlix.transactions.core.service

import com.flowlix.transactions.core.config.PlatformCoreProperties
import com.flowlix.transactions.core.domain.TransactionForFinalization
import com.flowlix.transactions.core.domain.TransactionStatus
import com.flowlix.transactions.core.repository.TransactionRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TransactionFinalizationServiceTest {
    private val properties =
        PlatformCoreProperties(
            finalizeBatchSize = 2,
        )

    private val transactionRepository = mockk<TransactionRepository>()
    private val decisionService = mockk<TransactionDecisionService>()
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var service: TransactionFinalizationService

    private val fixedNow = Instant.parse("2026-04-05T10:00:00Z")

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
        service = TransactionFinalizationService(transactionRepository, decisionService, properties, meterRegistry, clock)
    }

    @AfterEach
    fun tearDown() {
        clearMocks(transactionRepository, decisionService)
    }

    @Test
    fun `finalize increments succeeded counter and records lag`() {
        val tx =
            TransactionForFinalization(
                id = UUID.randomUUID(),
                externalId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                createdAt = fixedNow.minusSeconds(5),
            )
        every { transactionRepository.claimReadyForFinalization(properties.finalizeBatchSize) } returns listOf(tx)
        every { decisionService.decideFinalStatus(tx.externalId) } returns TransactionStatus.SUCCEEDED
        every {
            transactionRepository.markFinalizedBatch(listOf(tx.id), TransactionStatus.SUCCEEDED, null)
        } returns 1
        every { transactionRepository.markFinalizedBatch(emptyList(), any(), any()) } returns 0

        service.finalizeDueTransactions()

        assertEquals(1.0, meterRegistry.counter("platform_core_transactions_finalized_succeeded_total").count())
        assertEquals(0.0, meterRegistry.counter("platform_core_transactions_finalized_failed_total").count())
        assertEquals(1L, meterRegistry.timer("platform_core_transactions_end_to_end_latency").count())

        verify { transactionRepository.markFinalizedBatch(listOf(tx.id), TransactionStatus.SUCCEEDED, null) }
    }

    @Test
    fun `failed decision records failure reason and increments failed counter`() {
        val tx =
            TransactionForFinalization(
                id = UUID.randomUUID(),
                externalId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                createdAt = fixedNow.minusSeconds(3),
            )
        every { transactionRepository.claimReadyForFinalization(properties.finalizeBatchSize) } returns listOf(tx)
        every { decisionService.decideFinalStatus(tx.externalId) } returns TransactionStatus.FAILED

        val failureSlot = slot<String>()
        every { transactionRepository.markFinalizedBatch(emptyList(), any(), any()) } returns 0
        every {
            transactionRepository.markFinalizedBatch(listOf(tx.id), TransactionStatus.FAILED, capture(failureSlot))
        } returns 1

        service.finalizeDueTransactions()

        assertEquals(0.0, meterRegistry.counter("platform_core_transactions_finalized_succeeded_total").count())
        assertEquals(1.0, meterRegistry.counter("platform_core_transactions_finalized_failed_total").count())
        assertEquals(1L, meterRegistry.timer("platform_core_transactions_end_to_end_latency").count())
        assertEquals("deterministic-failure", failureSlot.captured)
    }

    @Test
    fun `skips counters when mark finalized returns zero`() {
        val tx =
            TransactionForFinalization(
                id = UUID.randomUUID(),
                externalId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                createdAt = fixedNow.minusSeconds(7),
            )
        every { transactionRepository.claimReadyForFinalization(properties.finalizeBatchSize) } returns listOf(tx)
        every { decisionService.decideFinalStatus(tx.externalId) } returns TransactionStatus.SUCCEEDED
        every {
            transactionRepository.markFinalizedBatch(listOf(tx.id), TransactionStatus.SUCCEEDED, null)
        } returns 0
        every { transactionRepository.markFinalizedBatch(emptyList(), any(), any()) } returns 0

        service.finalizeDueTransactions()

        assertEquals(0.0, meterRegistry.counter("platform_core_transactions_finalized_succeeded_total").count())
        assertEquals(0.0, meterRegistry.counter("platform_core_transactions_finalized_failed_total").count())
        assertEquals(0L, meterRegistry.timer("platform_core_transactions_end_to_end_latency").count())
    }
}
