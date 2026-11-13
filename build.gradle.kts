plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "athena"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "athena.MainKt"
    applicationName = "athena-mcp"
}

dependencies {
    implementation(libs.mcp.server)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.onnxruntime)
    implementation(libs.djl.tokenizers)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mcp.client)
    testImplementation(libs.mcp.testing)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Integration tests need a database: Testcontainers (Docker) or ATHENA_TEST_DATABASE_URL.
    // The ONNX parity test needs the model: ATHENA_MODEL_DIR.
    environment("ATHENA_REPO_ROOT", rootDir.absolutePath)
    // Tests read these files, so changing them must re-run the tests.
    inputs.dir("fixtures")
    inputs.file("db/schema.sql")
    // Pass a local test database through when set (see PostgresIntegrationTest).
    System.getenv("ATHENA_TEST_DATABASE_URL")?.let { environment("ATHENA_TEST_DATABASE_URL", it) }
    inputs.property("testDatabase", System.getenv("ATHENA_TEST_DATABASE_URL") ?: "")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
