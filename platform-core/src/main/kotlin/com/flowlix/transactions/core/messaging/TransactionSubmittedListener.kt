package com.flowlix.transactions.core.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowlix.transactions.contracts.TransactionSubmittedEvent
import com.flowlix.transactions.core.service.TransactionIngestService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TransactionSubmittedListener(
    private val objectMapper: ObjectMapper,
    private val transactionIngestService: TransactionIngestService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${platform-core.kafka-topic:transaction-submitted}"],
        id = "platform-core-transaction-submitted",
    )
    fun onMessage(payload: String) {
        val event = objectMapper.readValue(payload, TransactionSubmittedEvent::class.java)
        transactionIngestService.ingest(event)
        logger.debug("Ingested externalId={}", event.externalId)
    }
}
