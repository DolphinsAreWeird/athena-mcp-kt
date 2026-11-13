package athena.http

import athena.BuildInfo
import athena.domain.InvalidArgumentException
import athena.domain.SearchMode
import athena.mcp.createMcpServer
import athena.search.HandbookService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("athena.http")

/** JSON for the REST API. */
val ApiJson = Json { encodeDefaults = true; explicitNulls = false; prettyPrint = false }

@Serializable
data class ErrorBody(val error: String)

@Serializable
data class Health(
    val status: String,
    val version: String,
    val sections: Int,
    val chunks: Int,
    val embeddedChunks: Int,
    val semanticSearch: Boolean,
    val okbCommit: String? = null,
    val lastIngest: String? = null,
)

/**
 * The HTTP side of the service:
 * - `POST/GET/DELETE /mcp` — MCP over Streamable HTTP (one MCP server object per session)
 * - `GET /search` — the same search as the `search_handbook` tool, as JSON, for Athena's website or chatbot
 * - `GET /programmes`, `GET /health`
 *
 * `Application.athenaModule` is an extension function: Ktor calls it with the application as `this`.
 */
fun Application.athenaModule(service: HandbookService, allowedHosts: List<String>? = null) {
    mcpStreamableHttp(path = "/mcp", allowedHosts = allowedHosts) { createMcpServer(service) }

    routing {
        get("/health") {
            call.handled {
                val stats = service.stats()
                call.respondJson(
                    Health(
                        status = if (stats.sections > 0) "ok" else "empty",
                        version = BuildInfo.VERSION,
                        sections = stats.sections,
                        chunks = stats.chunks,
                        embeddedChunks = stats.embeddedChunks,
                        semanticSearch = service.semanticAvailable && stats.embeddedChunks > 0,
                        okbCommit = stats.okbCommit,
                        lastIngest = stats.lastIngest,
                    ),
                )
            }
        }

        // GET /search?q=leave+of+absence&programme=ece-ms&k=5&mode=hybrid
        get("/search") {
            call.handled {
                val params = call.request.queryParameters
                val query = params["q"] ?: throw InvalidArgumentException("missing query parameter 'q'")
                val mode = params["mode"]?.let {
                    SearchMode.parse(it) ?: throw InvalidArgumentException("mode must be hybrid, lexical or semantic")
                } ?: SearchMode.HYBRID
                val k = params["k"]?.let { it.toIntOrNull() ?: throw InvalidArgumentException("k must be a number") }
                call.respondJson(
                    service.search(
                        query = query,
                        programme = params["programme"]?.takeIf { it.isNotBlank() },
                        topK = k ?: HandbookService.DEFAULT_TOP_K,
                        mode = mode,
                    ),
                )
            }
        }

        get("/programmes") {
            call.handled { call.respondJson(service.programmes()) }
        }

        // EXERCISE 2 (yours to write): GET /sections/{id...} mirroring the get_section tool. See EXERCISES.md.
    }
}

/**
 * `inline` + `reified`: the type T is known at runtime inside this function, so
 * `encodeToString(value)` can find T's serializer without us passing it in.
 */
suspend inline fun <reified T> ApplicationCall.respondJson(value: T, status: HttpStatusCode = HttpStatusCode.OK) =
    respondText(ApiJson.encodeToString(value), ContentType.Application.Json, status)

/** Maps bad input to 400 and anything else to 500 with a JSON body. */
suspend fun ApplicationCall.handled(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: InvalidArgumentException) {
        respondJson(ErrorBody(e.message ?: "invalid request"), HttpStatusCode.BadRequest)
    } catch (e: java.sql.SQLException) {
        log.error("Database error", e)
        respondJson(ErrorBody("database unavailable"), HttpStatusCode.ServiceUnavailable)
    } catch (e: Exception) {
        log.error("Request failed", e)
        respondJson(ErrorBody("internal error"), HttpStatusCode.InternalServerError)
    }
}
