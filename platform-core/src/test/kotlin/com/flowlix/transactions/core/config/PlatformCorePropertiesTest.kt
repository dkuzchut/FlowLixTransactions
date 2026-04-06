package com.flowlix.transactions.core.config

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PlatformCorePropertiesTest {
    @Test
    fun `finalize batch size must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformCoreProperties(finalizeBatchSize = 0)
        }
    }
}
