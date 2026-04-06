package com.flowlix.transactions.core.config

import com.flowlix.transactions.core.config.PlatformCoreProperties
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.util.backoff.FixedBackOff

@Configuration
class KafkaConsumerConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun kafkaErrorHandler(
        kafkaTemplate: KafkaTemplate<Any, Any>,
        properties: PlatformCoreProperties,
    ): DefaultErrorHandler {
        // Keep retry bounded to avoid consumer stalls on poison messages.
        // After retries, route to a DLQ topic for inspection/replay.
        val dlqTopic = "${properties.kafkaTopic}.dlq"
        val recoverer =
            DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
                TopicPartition(dlqTopic, record.partition())
            }

        val backOff = FixedBackOff(200L, 3L)
        val handler = DefaultErrorHandler(recoverer, backOff)
        handler.setRetryListeners(
            RetryListener { record: ConsumerRecord<*, *>, ex: Exception, deliveryAttempt: Int ->
                logger.warn(
                    "Kafka listener error; attempt={} topic={} partition={} offset={}",
                    deliveryAttempt,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    ex,
                )
            },
        )
        return handler
    }
}
