package com.flowlix.transactions.core.service

import com.flowlix.transactions.core.domain.TransactionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class TransactionDecisionServiceTest {
    private val service = TransactionDecisionService()

    @Test
    fun `decision is deterministic for same external id`() {
        val externalId = UUID.fromString("48caed20-f7dd-4e6d-8d7b-cb383bded67e")

        val first = service.decideFinalStatus(externalId)
        val second = service.decideFinalStatus(externalId)

        assertEquals(first, second)
    }

    @Test
    fun `decision returns only terminal statuses`() {
        val statuses =
            (1..200)
                .map { service.decideFinalStatus(UUID.randomUUID()) }
                .toSet()

        assertEquals(setOf(TransactionStatus.SUCCEEDED, TransactionStatus.FAILED), statuses)
    }
}
