package com.flowlix.transactions.generator

import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("data-generator")
    val config = loadGeneratorConfigFromEnv()
    val publishConfig =
        PublishConfig(
            ratePerSecond = defaultRatePerSecond,
            durationSeconds = defaultDurationSeconds,
            bootstrapServers = config.bootstrapServers,
            topic = config.topic,
            partitionKeying = config.partitionKeying,
            partitions = config.partitions,
        )
    logger.info(
        "Starting publish run. rate={} duration={} bootstrapServers={} topic={} keying={} partitions={}",
        publishConfig.ratePerSecond,
        publishConfig.durationSeconds,
        publishConfig.bootstrapServers,
        publishConfig.topic,
        publishConfig.partitionKeying,
        publishConfig.partitions,
    )

    KafkaLoadPublisher.runWithRetry(publishConfig)
}
