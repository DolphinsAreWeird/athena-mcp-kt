package athena.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AppConfigTest {
    @Test
    fun `libpq url with credentials becomes a JDBC url`() {
        val config = "postgresql://athena:s%40cret@db:5433/athena?sslmode=require".toDatabaseConfig()
        assertEquals("jdbc:postgresql://db:5433/athena?sslmode=require", config.jdbcUrl)
        assertEquals("athena", config.user)
        assertEquals("s@cret", config.password)   // percent-decoded
    }

    @Test
    fun `url without credentials or port`() {
        val config = "postgresql://localhost/athena".toDatabaseConfig()
        assertEquals("jdbc:postgresql://localhost/athena", config.jdbcUrl)
        assertNull(config.user)
        assertNull(config.password)
    }

    @Test
    fun `jdbc urls pass through and other schemes are rejected`() {
        assertEquals("jdbc:postgresql://x/y", "jdbc:postgresql://x/y".toDatabaseConfig().jdbcUrl)
        assertFailsWith<IllegalArgumentException> { "mysql://x/y".toDatabaseConfig() }
    }

    @Test
    fun `password never appears in toString`() {
        val text = "postgresql://u:hunter2@h/d".toDatabaseConfig().toString()
        assertFalse("hunter2" in text)
    }

    @Test
    fun `fromEnv reads port, hosts and database`() {
        val config = AppConfig.fromEnv(
            mapOf(
                "DATABASE_URL" to "postgresql://h/d",
                "PORT" to "9090",
                "ATHENA_ALLOWED_HOSTS" to "athena.example, localhost ,",
                "ATHENA_MODEL_DIR" to "/nonexistent/model",
            ),
        )
        assertEquals(9090, config.port)
        assertEquals(listOf("athena.example", "localhost"), config.allowedHosts)
        assertEquals("jdbc:postgresql://h/d", config.database.jdbcUrl)
        assertEquals("/nonexistent/model", config.modelDir.toString())
    }
}
