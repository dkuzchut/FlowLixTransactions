package com.flowlix.transactions.core.service

import com.flowlix.transactions.core.domain.TransactionStatus
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TransactionDecisionService {
    fun decideFinalStatus(externalId: UUID): TransactionStatus {
        val bucket = Math.floorMod(externalId.hashCode(), 10)
        return if (bucket == 0) TransactionStatus.FAILED else TransactionStatus.SUCCEEDED
    }
}
