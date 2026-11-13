package athena.config

import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.text.Charsets.UTF_8

/**
 * Everything the service needs, read once at start-up from environment variables.
 *
 * A `data class` gets equals/hashCode/toString/copy for free, which is all a config holder needs.
 */
data class AppConfig(
    val database: DatabaseConfig,
    /** Folder with `model.onnx` (or another `.onnx` file) and `tokenizer.json`. `null` = lexical search only. */
    val modelDir: Path?,
    val port: Int,
    /** Host names allowed to call `/mcp` (DNS-rebinding protection). `null` = localhost only. */
    val allowedHosts: List<String>?,
) {
    companion object {
        const val DEFAULT_DATABASE_URL = "postgresql://localhost:5432/athena"

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            // `?.` and `?:` chain the "maybe missing" cases without a single if/else.
            val modelDir = env["ATHENA_MODEL_DIR"]?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                ?: defaultModelDir().takeIf { Files.isDirectory(it) }
            return AppConfig(
                database = (env["DATABASE_URL"] ?: DEFAULT_DATABASE_URL).toDatabaseConfig(),
                modelDir = modelDir,
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                allowedHosts = env["ATHENA_ALLOWED_HOSTS"]
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() },
            )
        }

        /** Where `athena-ingest download-model` puts the model. */
        fun defaultModelDir(): Path =
            Path.of(System.getProperty("user.home"), ".cache", "athena-mcp", "models", "paraphrase-multilingual-MiniLM-L12-v2")
    }
}

data class DatabaseConfig(val jdbcUrl: String, val user: String?, val password: String?) {
    /** Never print the password, even in a debug log. */
    override fun toString(): String =
        "DatabaseConfig(jdbcUrl=$jdbcUrl, user=$user, password=${if (password == null) "null" else "***"})"
}

/**
 * Converts a libpq-style URL — the format psycopg (the Python ingest job) and docker-compose use,
 * `postgresql://user:pass@host:5432/db?sslmode=require` — into what the JDBC driver wants.
 *
 * This is an *extension function*: it adds a method to `String` without subclassing it.
 */
fun String.toDatabaseConfig(): DatabaseConfig {
    if (startsWith("jdbc:")) return DatabaseConfig(this, null, null)
    val uri = URI(this)
    require(uri.scheme == "postgresql" || uri.scheme == "postgres") {
        "DATABASE_URL must start with postgresql:// (got '$this')"
    }
    val userInfo = uri.rawUserInfo?.split(":", limit = 2)?.map { URLDecoder.decode(it, UTF_8) }
    val host = uri.host ?: "localhost"
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val query = uri.rawQuery?.let { "?$it" } ?: ""
    return DatabaseConfig(
        jdbcUrl = "jdbc:postgresql://$host$port${uri.rawPath}$query",
        user = userInfo?.getOrNull(0),
        password = userInfo?.getOrNull(1),
    )
}
