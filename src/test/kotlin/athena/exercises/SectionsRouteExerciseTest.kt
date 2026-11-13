package athena.exercises

import athena.domain.Section
import athena.http.ApiJson
import athena.http.athenaModule
import athena.search.HandbookService
import athena.testing.FakeHandbookStore
import athena.testing.HashingEmbedder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** EXERCISE 2 — see EXERCISES.md. Delete the @Disabled line, then make these pass. */
@Disabled("Exercise 2: add GET /sections/{id...} in athena/http/Routes.kt, then delete this line")
class SectionsRouteExerciseTest {
    private val service = HandbookService(FakeHandbookStore(), HashingEmbedder())

    @Test
    fun `returns the section as JSON, ids contain slashes`() = testApplication {
        application { athenaModule(service) }
        val response = client.get("/sections/aice-beng/32-academic-probation")
        assertEquals(HttpStatusCode.OK, response.status)
        val section = ApiJson.decodeFromString<Section>(response.bodyAsText())
        assertEquals("Academic Probation", section.title)
        assertEquals("aice-beng/32-academic-probation.md", section.citation.path)
    }

    @Test
    fun `unknown id is a 404 with suggestions`() = testApplication {
        application { athenaModule(service) }
        val response = client.get("/sections/aice-beng/probation")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue("aice-beng/32-academic-probation" in response.bodyAsText())
    }
}
