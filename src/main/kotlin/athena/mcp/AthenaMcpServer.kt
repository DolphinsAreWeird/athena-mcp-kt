package athena.mcp

import athena.BuildInfo
import athena.domain.InvalidArgumentException
import athena.domain.SearchMode
import athena.domain.SectionLookup
import athena.search.HandbookService
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("athena.mcp")

/** JSON for `structuredContent`: same field names as the REST API. */
internal val ToolJson = Json { encodeDefaults = true; explicitNulls = false }

private val READ_ONLY = ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)

const val SERVER_INSTRUCTIONS = """Athena serves CMKL University's reviewed handbook knowledge base: the B.Eng. in AiCE, M.S. in AiCE and M.S. in ECE student handbooks, university-wide policies, and committee minutes.
Use search_handbook for any question about CMKL rules, courses, faculty or procedures, and answer only from what it returns.
Cite every fact with its handbook, section and path. If a student names their programme, pass it as `programme`.
Committee decisions (pages under minutes/) can change handbook rules from an effective date: mention both when they differ."""

/** Builds an MCP server exposing the handbook tools. Cheap: Streamable HTTP makes one per session. */
fun createMcpServer(service: HandbookService): Server {
    val server = Server(
        serverInfo = Implementation(name = "athena-handbook", version = BuildInfo.VERSION),
        options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        instructions = SERVER_INSTRUCTIONS,
    )
    server.registerHandbookTools(service)
    return server
}

/** An extension function on the SDK's `Server`: reads like a method of it, lives in our code. */
fun Server.registerHandbookTools(service: HandbookService) {
    addTool(
        name = "search_handbook",
        title = "Search the CMKL handbooks",
        description = "Search CMKL University's student handbooks, policies and committee minutes. " +
            "Returns the best-matching passages, each with its citation (handbook, section, file path). " +
            "Hybrid search: keywords plus multilingual meaning, so Thai questions work too.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "The question or keywords, e.g. 'notice period to return from leave of absence'")
                }
                putJsonObject("programme") {
                    put("type", "string")
                    put("description", "Optional programme code to restrict to (its own pages plus university-wide ones): aice-beng, aice-ms, ece-ms. Call list_programmes for all codes.")
                }
                putJsonObject("top_k") {
                    put("type", "integer")
                    put("minimum", 1)
                    put("maximum", HandbookService.MAX_TOP_K)
                    put("default", HandbookService.DEFAULT_TOP_K)
                    put("description", "How many sections to return")
                }
                putJsonObject("mode") {
                    put("type", "string")
                    putJsonArray("enum") { SearchMode.entries.forEach { add(JsonPrimitive(it.name.lowercase())) } }
                    put("default", "hybrid")
                    put("description", "hybrid (default), lexical (keywords only) or semantic (meaning only)")
                }
            },
            required = listOf("query"),
        ),
        toolAnnotations = READ_ONLY,
    ) { request ->
        toolCall {
            val args = request.args
            val mode = args.string("mode")?.let {
                SearchMode.parse(it) ?: throw InvalidArgumentException("mode must be hybrid, lexical or semantic")
            } ?: SearchMode.HYBRID
            val response = service.search(
                query = args.requireString("query"),
                programme = args.string("programme"),
                topK = args.int("top_k") ?: HandbookService.DEFAULT_TOP_K,
                mode = mode,
            )
            success(Formatting.search(response), ToolJson.encodeToJsonElement(response).jsonObject)
        }
    }

    addTool(
        name = "get_section",
        title = "Read one handbook section",
        description = "Return the full text of one handbook section by its id (as given in search results, " +
            "e.g. 'ece-ms/23-returning-from-a-leave-of-absence'), with its citation.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "Section id: the page path without .md")
                }
            },
            required = listOf("id"),
        ),
        toolAnnotations = READ_ONLY,
    ) { request ->
        toolCall {
            // A `when` over a sealed type must cover every case — here, found and not found.
            when (val lookup = service.section(request.args.requireString("id"))) {
                is SectionLookup.Found ->
                    success(Formatting.section(lookup.section), ToolJson.encodeToJsonElement(lookup.section).jsonObject)
                is SectionLookup.NotFound -> failure(
                    "No section with id '${lookup.id}'." +
                        if (lookup.suggestions.isEmpty()) " Use search_handbook to find the right id."
                        else " Did you mean: ${lookup.suggestions.joinToString()}?",
                )
            }
        }
    }

    addTool(
        name = "list_programmes",
        title = "List programmes",
        description = "List the programmes (and university-wide collections) in the knowledge base, with their codes and section counts.",
        toolAnnotations = READ_ONLY,
    ) { _ ->
        toolCall {
            val programmes = service.programmes()
            success(Formatting.programmes(programmes), buildJsonObject { put("programmes", ToolJson.encodeToJsonElement(programmes)) })
        }
    }

    addTool(
        name = "recent_changes",
        title = "Recent handbook changes",
        description = "List approved changes to the handbook knowledge base (from its review log), newest first: " +
            "who changed which section, when, and how (approved edit, new page, deletion, undo).",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("since") {
                    put("type", "string")
                    put("description", "Only changes on or after this date, e.g. 2026-10-01")
                }
                putJsonObject("programme") {
                    put("type", "string")
                    put("description", "Optional programme code, e.g. aice-beng")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("minimum", 1)
                    put("maximum", 100)
                    put("default", 20)
                }
            },
        ),
        toolAnnotations = READ_ONLY,
    ) { request ->
        toolCall {
            val args = request.args
            val since = parseSince(args.string("since"))
            val changes = service.recentChanges(since, args.string("programme"), args.int("limit") ?: 20)
            success(
                Formatting.changes(changes, since?.toString()),
                buildJsonObject { put("changes", ToolJson.encodeToJsonElement(changes)) },
            )
        }
    }

    // EXERCISE 3 (yours to write): register a `find_decisions` tool here. See EXERCISES.md.
}

// --- small helpers -------------------------------------------------------------------------------

private fun success(text: String, structured: JsonObject? = null) =
    CallToolResult(content = listOf(TextContent(text)), structuredContent = structured)

private fun failure(text: String) = CallToolResult(content = listOf(TextContent(text)), isError = true)

/**
 * Runs a tool body and turns failures into MCP tool errors (`isError = true`) the model can read
 * and recover from, instead of protocol errors. Cancellation must always be re-thrown so coroutines
 * can stop cleanly.
 */
private suspend fun toolCall(block: suspend () -> CallToolResult): CallToolResult =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: InvalidArgumentException) {
        failure("Invalid arguments: ${e.message}")
    } catch (e: java.sql.SQLException) {
        log.error("Database error in tool call", e)
        failure("The handbook database is unavailable right now (${e.message?.lineSequence()?.firstOrNull()}). Try again later.")
    } catch (e: Exception) {
        log.error("Tool call failed", e)
        failure("Internal error: ${e.message}")
    }

/** Extension *property*: `request.args` is never null, so tool bodies don't repeat the null check. */
private val CallToolRequest.args: JsonObject get() = arguments ?: JsonObject(emptyMap())

internal fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.requireString(name: String): String =
    string(name) ?: throw InvalidArgumentException("'$name' is required")

internal fun JsonObject.int(name: String): Int? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    return primitive.intOrNull
        ?: primitive.contentOrNull?.toDoubleOrNull()?.toInt()
        ?: throw InvalidArgumentException("'$name' must be a whole number")
}
