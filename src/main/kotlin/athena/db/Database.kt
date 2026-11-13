package athena.db

import athena.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/** A small connection pool. The server only reads; the Python ingest job is the only writer. */
fun createDataSource(config: DatabaseConfig, readOnly: Boolean = true): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            config.user?.let { username = it }
            config.password?.let { password = it }
            maximumPoolSize = 4
            poolName = "athena"
            isReadOnly = readOnly
            // Start even if Postgres isn't up yet: tools then answer "database unavailable"
            // instead of the MCP server failing to launch inside Claude Desktop.
            initializationFailTimeout = -1
            connectionTimeout = 5_000
        },
    )

/**
 * Runs one SQL query and maps every row.
 *
 * - `suspend` + `withContext(Dispatchers.IO)`: JDBC blocks its thread, so we move that work to the
 *   IO thread pool and the caller's coroutine simply waits without blocking anything.
 * - `bind: PreparedStatement.() -> Unit` is a *lambda with receiver*: inside it, `this` is the
 *   statement, so callers write `setString(1, q)` directly.
 * - `use { }` closes the connection/statement/result set even if an exception is thrown.
 */
suspend fun <T> DataSource.query(
    sql: String,
    bind: PreparedStatement.() -> Unit = {},
    map: ResultSet.() -> T,
): List<T> = withContext(Dispatchers.IO) {
    connection.use { conn ->
        conn.prepareStatement(sql).use { statement ->
            statement.bind()
            statement.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.map()) }
            }
        }
    }
}

/** Reads a Postgres `text[]` column as a Kotlin list (empty when NULL). */
fun ResultSet.stringList(column: String): List<String> =
    (getArray(column)?.array as? Array<*>)?.map { it.toString() } ?: emptyList()

/** pgvector's text format: `[0.1,0.2,...]`. Bound as a string and cast with `?::vector` in SQL. */
fun FloatArray.toPgVector(): String = joinToString(separator = ",", prefix = "[", postfix = "]")
