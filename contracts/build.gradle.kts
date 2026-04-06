plugins {
    kotlin("jvm")
}

group = "com.flowlix.transactions"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations:2.17.0")
}

