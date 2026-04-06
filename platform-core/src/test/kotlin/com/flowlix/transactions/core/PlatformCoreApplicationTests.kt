package com.flowlix.transactions.core

import com.flowlix.transactions.core.repository.TransactionRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate

@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    ],
)
class PlatformCoreApplicationTests {
    @MockBean
    lateinit var transactionRepository: TransactionRepository

    @MockBean
    lateinit var kafkaTemplate: KafkaTemplate<Any, Any>

    @Test
    fun contextLoads() {
    }
}
