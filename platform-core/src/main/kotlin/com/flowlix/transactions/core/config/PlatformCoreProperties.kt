package com.flowlix.transactions.core.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "platform-core")
data class PlatformCoreProperties(
    val kafkaTopic: String = "transaction-submitted",
    val finalizeBatchSize: Int = 500,
) {
    init {
        require(finalizeBatchSize > 0) { "platform-core.finalize-batch-size must be > 0" }
    }
}
