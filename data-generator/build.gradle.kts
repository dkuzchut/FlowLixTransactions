plugins {
    kotlin("jvm")
    application
}

group = "com.flowlix.transactions"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":contracts"))
    implementation("org.apache.kafka:kafka-clients:3.6.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.slf4j:slf4j-simple:2.0.13")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

application {
    mainClass.set("com.flowlix.transactions.generator.DataGeneratorKt")
}
