plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20" // ← добавь это
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // HTTP-клиент для Telegram/Supabase
    implementation("io.ktor:ktor-client-java:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

application {
    mainClass.set("app.MainKt")
}

tasks.withType<Jar> {
    manifest { attributes["Main-Class"] = "app.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
