package com.flowlix.transactions.core.repository

import com.flowlix.transactions.core.domain.TransactionForFinalization
import com.flowlix.transactions.core.domain.TransactionStatus
import com.flowlix.transactions.contracts.TransactionSubmittedEvent
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class TransactionRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun insertInProgressIfAbsent(event: TransactionSubmittedEvent): Boolean {
        val params =
            MapSqlParameterSource()
                .addValue("externalId", event.externalId)
                .addValue("merchantId", event.merchantId)
                .addValue("amountMinor", event.amountMinor)
                .addValue("currency", event.currency)
                .addValue("status", TransactionStatus.IN_PROGRESS.name)
                .addValue("eventCreatedAt", event.createdAt.atOffset(java.time.ZoneOffset.UTC))

        val affected =
            jdbcTemplate.update(
                """
                INSERT INTO transactions
                    (external_id, merchant_id, amount_minor, currency, status, event_created_at)
                VALUES
                    (:externalId, :merchantId, :amountMinor, :currency, :status, :eventCreatedAt)
                ON CONFLICT (external_id) DO NOTHING
                """.trimIndent(),
                params,
            )

        return affected > 0
    }

    fun claimReadyForFinalization(limit: Int): List<TransactionForFinalization> {
        val params =
            MapSqlParameterSource()
                .addValue("status", TransactionStatus.IN_PROGRESS.name)
                .addValue("limit", limit)

        return jdbcTemplate.query(
            """
            SELECT id, external_id, created_at
            FROM transactions
            WHERE status = :status
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """.trimIndent(),
            params,
        ) { rs, _ ->
            TransactionForFinalization(
                id = rs.getObject("id", UUID::class.java),
                externalId = rs.getObject("external_id", UUID::class.java),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
            )
        }
    }

    fun markFinalizedBatch(ids: List<UUID>, status: TransactionStatus, failureReason: String?): Int {
        if (ids.isEmpty()) return 0

        val params =
            MapSqlParameterSource()
                .addValue("ids", ids)
                .addValue("newStatus", status.name)
                .addValue("currentStatus", TransactionStatus.IN_PROGRESS.name)
                .addValue("failureReason", failureReason)

        return jdbcTemplate.update(
            """
            UPDATE transactions
            SET status = :newStatus,
                updated_at = now(),
                failure_reason = :failureReason
            WHERE id IN (:ids)
              AND status = :currentStatus
            """.trimIndent(),
            params,
        )
    }
}
