package athena.mcp

import athena.search.HandbookService
import athena.testing.FakeHandbookStore
import athena.testing.HashingEmbedder
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Talks to the real MCP server through an in-memory transport, exactly as Claude would over stdio.
 * `runBlocking`, not `runTest`: runTest's virtual clock would skip past the client's request timeout
 * while the server is still working on real threads.
 */
class McpToolsTest {
    private val service = HandbookService(FakeHandbookStore(), HashingEmbedder())

    private fun withClient(block: suspend (Client) -> Unit) = runBlocking {
        val server = createMcpServer(service)
        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
        val client = Client(clientInfo = Implementation(name = "test-client", version = "1.0"), options = ClientOptions())
        joinAll(
            launch { client.connect(clientTransport) },
            launch { server.createSession(serverTransport) },
        )
        try {
            block(client)
        } finally {
            client.close()
        }
    }

    private val CallToolResult.text: String get() = content.filterIsInstance<TextContent>().joinToString("\n") { it.text }

    @Test
    fun `lists the four read-only tools with their schemas`() = withClient { client ->
        val tools = client.listTools().tools.associateBy { it.name }
        assertEquals(setOf("search_handbook", "get_section", "list_programmes", "recent_changes"), tools.keys)
        tools.values.forEach { assertEquals(true, it.annotations?.readOnlyHint, it.name) }
        assertEquals(listOf("query"), tools.getValue("search_handbook").inputSchema.required)
        assertEquals(listOf("id"), tools.getValue("get_section").inputSchema.required)
    }

    @Test
    fun `server sends instructions telling the model to cite`() = withClient { client ->
        assertTrue(client.serverInstructions.orEmpty().contains("Cite every fact"))
    }

    @Test
    fun `search_handbook returns passages with citations, as text and as structured JSON`() = withClient { client ->
        val result = client.callTool("search_handbook", mapOf("query" to "notice to return from leave of absence", "programme" to "ece-ms"))
        assertNotEquals(true, result.isError)
        assertTrue("ece-ms/23-returning-from-a-leave-of-absence.md" in result.text, result.text)
        assertTrue("Cite each fact" in result.text)

        val structured = assertNotNull(result.structuredContent)
        val first = structured.getValue("results").jsonArray.first().jsonObject
        assertNotNull(first["citation"]?.jsonObject?.get("handbook"))
        assertEquals("ece-ms", structured.getValue("programme").jsonPrimitive.content)
    }

    @Test
    fun `bad arguments come back as tool errors the model can read`() = withClient { client ->
        val missing = client.callTool("search_handbook", emptyMap())
        assertEquals(true, missing.isError)
        assertTrue("'query' is required" in missing.text)

        val badMode = client.callTool("search_handbook", mapOf("query" to "x", "mode" to "fuzzy"))
        assertEquals(true, badMode.isError)

        val badProgramme = client.callTool("search_handbook", mapOf("query" to "x", "programme" to "law"))
        assertTrue("use one of" in badProgramme.text)
    }

    @Test
    fun `get_section returns the full page or suggestions`() = withClient { client ->
        val found = client.callTool("get_section", mapOf("id" to "aice-beng/32-academic-probation"))
        assertNotEquals(true, found.isError)
        assertTrue("# Academic Probation" in found.text)
        assertTrue("Leaving probation" in found.text)

        val missing = client.callTool("get_section", mapOf("id" to "aice-beng/probation"))
        assertEquals(true, missing.isError)
        assertTrue("Did you mean" in missing.text, missing.text)
    }

    @Test
    fun `list_programmes and recent_changes`() = withClient { client ->
        val programmes = client.callTool("list_programmes", emptyMap())
        assertTrue("ece-ms" in programmes.text)

        val changes = client.callTool("recent_changes", mapOf("since" to "2026-09-01"))
        assertTrue("Academic Probation" in changes.text)
        assertTrue("Returning from a Leave" !in changes.text)   // 2026-08-20 is before `since`

        val badDate = client.callTool("recent_changes", mapOf("since" to "last tuesday"))
        assertEquals(true, badDate.isError)
    }
}
