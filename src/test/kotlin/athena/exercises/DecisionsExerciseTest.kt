package athena.exercises

import athena.mcp.Decision
import athena.mcp.createMcpServer
import athena.mcp.parseDecisions
import athena.search.HandbookService
import athena.testing.FakeHandbookStore
import athena.testing.FixtureOkb
import athena.testing.HashingEmbedder
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** EXERCISE 3 — see EXERCISES.md. Delete the @Disabled line, then make these pass. */
@Disabled("Exercise 3: implement parseDecisions and a find_decisions tool, then delete this line")
class DecisionsExerciseTest {
    @Test
    fun `parses every decision line in a minutes page`() {
        val minutes = FixtureOkb.page("minutes/2026-08-20-gsc").body
        assertEquals(
            listOf(
                Decision(
                    code = "GSC-2026-08-D2",
                    programmes = listOf("M.S. in ECE"),
                    effective = "effective Spring 2027",
                    text = "The notice to return from a leave of absence is reduced from at least 75 days to at least 45 days before the start of the semester of return.",
                ),
                Decision(
                    code = "GSC-2026-08-D3",
                    programmes = listOf("M.S. in ECE", "M.S. in AiCE"),
                    effective = "effective immediately",
                    text = "The graduate internship course stays at 3 units and is offered only in the summer.",
                ),
            ),
            parseDecisions(minutes),
        )
        assertEquals(emptyList(), parseDecisions("## Decisions\n\nNothing was decided."))
    }

    @Test
    fun `find_decisions tool returns matching decisions with their minutes as the source`() = runBlocking {
        val server = createMcpServer(HandbookService(FakeHandbookStore(), HashingEmbedder()))
        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
        val client = Client(clientInfo = Implementation(name = "test", version = "1.0"))
        joinAll(launch { client.connect(clientTransport) }, launch { server.createSession(serverTransport) })

        val result = client.callTool("find_decisions", mapOf("topic" to "leave of absence"))
        val text = result.content.filterIsInstance<TextContent>().joinToString { it.text }
        assertTrue("GSC-2026-08-D2" in text, text)
        assertTrue("minutes/2026-08-20-gsc" in text, "cites the minutes page: $text")
        client.close()
    }
}
