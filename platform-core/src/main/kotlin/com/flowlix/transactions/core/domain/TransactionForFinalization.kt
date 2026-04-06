package com.flowlix.transactions.core.domain

import java.time.Instant
import java.util.UUID

data class TransactionForFinalization(
    val id: UUID,
    val externalId: UUID,
    val createdAt: Instant,
)
