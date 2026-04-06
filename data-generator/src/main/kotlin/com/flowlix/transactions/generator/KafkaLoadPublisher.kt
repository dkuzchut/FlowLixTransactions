package com.flowlix.transactions.generator

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.Properties
import java.util.concurrent.locks.LockSupport
import kotlin.random.Random
import kotlin.system.exitProcess

object KafkaLoadPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun runWithRetry(config: PublishConfig) {
        val timeoutSeconds = System.getenv("KAFKA_CONNECT_TIMEOUT_SECONDS")?.toLongOrNull() ?: 600L
        val deadlineNanos =
            if (timeoutSeconds <= 0) {
                Long.MAX_VALUE
            } else {
                System.nanoTime() + timeoutSeconds * 1_000_000_000L
            }
        var attempt = 0
        while (true) {
            try {
                run(config)
                return
            } catch (e: ConfigException) {
                if (System.nanoTime() > deadlineNanos) throw e
                attempt++
                val backoffMs = (250L * attempt).coerceAtMost(5_000L)
                logger.warn("Kafka config not ready ({}); retrying in {}ms", e.message, backoffMs)
                Thread.sleep(backoffMs)
            } catch (e: KafkaException) {
                if (System.nanoTime() > deadlineNanos) throw e
                attempt++
                val backoffMs = (250L * attempt).coerceAtMost(5_000L)
                logger.warn("Kafka not ready ({}); retrying in {}ms", e.message, backoffMs)
                Thread.sleep(backoffMs)
            }
        }
    }

    private fun run(config: PublishConfig) {
        val mapper =
            jacksonObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        val props =
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.ACKS_CONFIG, "all")
                put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
                // Small batching improves throughput while keeping latency reasonable for load generation.
                put(ProducerConfig.LINGER_MS_CONFIG, 5)
                put(ProducerConfig.BATCH_SIZE_CONFIG, 32 * 1024)
                put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4")
                put(ProducerConfig.CLIENT_ID_CONFIG, "data-generator")
            }

        val stats = PublishStats()
        val wallStartNanos = System.nanoTime()

        KafkaProducer<String, String>(props).use { producer ->
            val total = config.totalMessages
            val periodNanos = 1_000_000_000L / config.ratePerSecond
            var sequence = 0L
            val random = Random.Default

            while (sequence < total) {
                val targetTimeNanos = wallStartNanos + sequence * periodNanos
                var now = System.nanoTime()
                while (now < targetTimeNanos) {
                    LockSupport.parkNanos(targetTimeNanos - now)
                    now = System.nanoTime()
                }
                stats.recordScheduleLag(now - targetTimeNanos)

                val event = SyntheticTransactionFactory.next(random, merchantIdOverride = config.merchantId)
                val key =
                    SyntheticTransactionFactory.partitionKey(
                        event,
                        config.partitionKeying,
                        sequence,
                        config.partitions,
                    )
                val json = mapper.writeValueAsString(event)
                val record = ProducerRecord(config.topic, key, json)
                val sendStartNanos = System.nanoTime()
                producer.send(record) { _, ex ->
                    if (ex != null) {
                        stats.recordError()
                    } else {
                        stats.recordSuccess(System.nanoTime() - sendStartNanos)
                    }
                }
                sequence++
            }

            producer.flush()
        }

        val wallSeconds = (System.nanoTime() - wallStartNanos) / 1_000_000_000.0
        val snap = stats.snapshot()
        printReport(config, wallSeconds, snap)
    }

    private fun printReport(config: PublishConfig, wallSeconds: Double, snap: PublishStatsSnapshot) {
        val attempted = config.totalMessages
        val achievedRps = snap.successCount / wallSeconds
        logger.info("--- Publish run complete ---")
        logger.info("Target rate: {} msg/s", config.ratePerSecond)
        logger.info("Duration (wall): {} s", "%.2f".format(wallSeconds))
        logger.info("Attempted sends: {}", attempted)
        logger.info("Acknowledged ok: {}", snap.successCount)
        logger.info("Errors: {}", snap.errorCount)
        logger.info("Achieved ack RPS: {}", "%.0f".format(achievedRps))
        logger.info(
            "Publish latency (ms, ack-based): p50={} p95={} p99={}",
            snap.percentile(50.0)?.let { "%.3f".format(it) } ?: "n/a",
            snap.percentile(95.0)?.let { "%.3f".format(it) } ?: "n/a",
            snap.percentile(99.0)?.let { "%.3f".format(it) } ?: "n/a",
        )
        logger.info(
            "Schedule lag (ms): avg={} max={}",
            "%.3f".format(snap.avgScheduleLagMillis),
            "%.3f".format(snap.maxScheduleLagMillis),
        )
        if (snap.errorCount > 0) {
            exitProcess(1)
        }
    }
}
