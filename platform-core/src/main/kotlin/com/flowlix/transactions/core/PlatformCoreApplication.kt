package com.flowlix.transactions.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
class PlatformCoreApplication

fun main(args: Array<String>) {
    runApplication<PlatformCoreApplication>(*args)
}
