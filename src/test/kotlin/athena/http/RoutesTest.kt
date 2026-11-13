package athena.http

import athena.domain.SearchMode
import athena.domain.SearchResponse
import athena.search.HandbookService
import athena.testing.FakeHandbookStore
import athena.testing.HashingEmbedder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutesTest {
    private val service = HandbookService(FakeHandbookStore(), HashingEmbedder())

    @Test
    fun `GET search mirrors the MCP tool`() = testApplication {
        application { athenaModule(service) }
        val response = client.get("/search?q=tuition+refund&k=2&mode=lexical")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = ApiJson.decodeFromString<SearchResponse>(response.bodyAsText())
        assertEquals(SearchMode.LEXICAL, body.mode)
        assertEquals("general/policies/tuition-refunds", body.results.first().sectionId)
        assertTrue(body.results.size <= 2)
    }

    @Test
    fun `GET search validates its parameters`() = testApplication {
        application { athenaModule(service) }
        assertEquals(HttpStatusCode.BadRequest, client.get("/search").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/search?q=x&k=lots").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/search?q=x&programme=nope").status)
        assertTrue("missing query parameter" in client.get("/search").bodyAsText())
    }

    @Test
    fun `health and programmes`() = testApplication {
        application { athenaModule(service) }
        val health = ApiJson.decodeFromString<Health>(client.get("/health").bodyAsText())
        assertEquals("ok", health.status)
        assertEquals(true, health.semanticSearch)
        assertTrue("ece-ms" in client.get("/programmes").bodyAsText())
    }
}
