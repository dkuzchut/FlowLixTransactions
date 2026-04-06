package com.flowlix.transactions.contracts

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

data class TransactionSubmittedEvent(
    @JsonProperty("externalId")
    val externalId: UUID,
    @JsonProperty("merchantId")
    val merchantId: String,
    @JsonProperty("amountMinor")
    val amountMinor: Long,
    @JsonProperty("currency")
    val currency: String,
    @JsonProperty("createdAt")
    val createdAt: Instant,
)

