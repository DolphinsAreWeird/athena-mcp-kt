package athena

import athena.config.AppConfig
import athena.db.createDataSource
import athena.domain.SearchMode
import athena.embed.Embedder
import athena.embed.OnnxEmbedder
import athena.http.ApiJson
import athena.http.athenaModule
import athena.mcp.createMcpServer
import athena.search.HandbookService
import athena.search.PostgresHandbookStore
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import org.slf4j.LoggerFactory
import java.io.PrintStream
import kotlin.system.exitProcess

object BuildInfo {
    const val VERSION = "0.1.0"
}

private val log = LoggerFactory.getLogger("athena")

private const val USAGE = """Usage: athena-mcp <command>
  serve              HTTP server: MCP at /mcp (Streamable HTTP) + REST /search, /programmes, /health  [default]
  stdio              MCP over stdin/stdout, for Claude Desktop / Claude Code
  search <query...>  one search, printed as JSON (for debugging)

Environment: DATABASE_URL (postgresql://user:pass@host:5432/athena), ATHENA_MODEL_DIR, PORT, ATHENA_ALLOWED_HOSTS"""

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "serve"
    // In stdio mode stdout *is* the MCP channel. Keep the real stdout for the protocol and point
    // System.out at stderr, so no library can corrupt the stream by printing (kotlin-logging, pulled
    // in by the MCP SDK, prints a start-up banner to stdout).
    val protocolOut: PrintStream = System.out
    if (command == "stdio") System.setOut(System.err)

    val config = AppConfig.fromEnv()
    when (command) {
        "serve" -> withService(config) { service -> serveHttp(service, config) }
        "stdio" -> withService(config) { service -> serveStdio(service, protocolOut) }
        "search" -> withService(config) { service ->
            val query = args.drop(1).joinToString(" ")
            val response = runBlocking { service.search(query, mode = SearchMode.HYBRID) }
            println(ApiJson.encodeToString(response))
        }
        "-h", "--help", "help" -> println(USAGE)
        else -> {
            System.err.println("Unknown command '$command'\n\n$USAGE")
            exitProcess(2)
        }
    }
}

/** Opens the pool and the model, runs [block], and always closes both. */
private fun withService(config: AppConfig, block: (HandbookService) -> Unit) {
    log.info("Database: {}", config.database)
    createDataSource(config.database).use { dataSource ->
        val embedder = loadEmbedder(config)
        try {
            block(HandbookService(PostgresHandbookStore(dataSource), embedder))
        } finally {
            embedder?.close()
        }
    }
}

/** A missing or broken model is not fatal: the server still answers with lexical search. */
private fun loadEmbedder(config: AppConfig): Embedder? {
    val dir = config.modelDir ?: run {
        log.warn("No embedding model (set ATHENA_MODEL_DIR): semantic search is off, lexical search only")
        return null
    }
    return runCatching { OnnxEmbedder(dir) }
        .onSuccess { log.info("Embedding model: {} ({} dims)", it.name, it.dimensions) }
        .onFailure { log.warn("Could not load the embedding model from {}: {} — lexical search only", dir, it.message) }
        .getOrNull()
}

private fun serveHttp(service: HandbookService, config: AppConfig) {
    log.info("Listening on http://localhost:{} (MCP at /mcp, REST at /search)", config.port)
    embeddedServer(CIO, host = "0.0.0.0", port = config.port) {
        athenaModule(service, config.allowedHosts)
    }.start(wait = true)
}

/**
 * MCP over stdio. [protocolOut] is the process's real stdout; everything else (logs via
 * logback.xml, stray prints via System.setOut in main) goes to stderr.
 */
private fun serveStdio(service: HandbookService, protocolOut: PrintStream) {
    val server = createMcpServer(service)
    val transport = StdioServerTransport(
        input = System.`in`.asInput(),
        output = protocolOut.asSink().buffered(),
    )
    runBlocking {
        val session = server.createSession(transport)
        val done = Job()
        session.onClose { done.complete() }
        done.join() // the client closed stdin (or the session ended)
        log.info("MCP session closed; shutting down")
        server.close()
        // The SDK starts helper coroutines (e.g. list-changed notifications) inside this scope;
        // runBlocking would wait for them forever, so cancel whatever is left.
        coroutineContext.cancelChildren()
    }
}
