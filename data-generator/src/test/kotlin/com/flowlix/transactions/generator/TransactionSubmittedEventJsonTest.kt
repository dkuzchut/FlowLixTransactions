package com.flowlix.transactions.generator

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.flowlix.transactions.contracts.TransactionSubmittedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TransactionSubmittedEventJsonTest {
    private val mapper =
        jacksonObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `serializes and deserializes round trip with camelCase keys`() {
        val original =
            TransactionSubmittedEvent(
                externalId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
                merchantId = "m-1",
                amountMinor = 12499L,
                currency = "EUR",
                createdAt = Instant.parse("2026-04-04T15:00:00.456Z"),
            )
        val json = mapper.writeValueAsString(original)
        val parsed: TransactionSubmittedEvent = mapper.readValue(json)
        assertEquals(original, parsed)
    }

    @Test
    fun `json uses expected property names`() {
        val event =
            TransactionSubmittedEvent(
                externalId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
                merchantId = "m-1",
                amountMinor = 12499L,
                currency = "EUR",
                createdAt = Instant.parse("2026-04-04T15:00:00.456Z"),
            )
        val json = mapper.writeValueAsString(event)
        assertTrue(json.contains("\"externalId\""))
        assertTrue(json.contains("\"merchantId\""))
        assertTrue(json.contains("\"amountMinor\""))
        assertTrue(json.contains("\"currency\""))
        assertTrue(json.contains("\"createdAt\""))
    }
}
