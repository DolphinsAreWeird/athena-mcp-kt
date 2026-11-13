package athena.search

import athena.domain.InvalidArgumentException
import athena.domain.SearchMode
import athena.domain.SectionLookup
import athena.testing.FakeHandbookStore
import athena.testing.HashingEmbedder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandbookServiceTest {
    private val store = FakeHandbookStore()
    private val service = HandbookService(store, HashingEmbedder())

    @Test
    fun `hybrid search returns one passage per section, each with a citation`() = runTest {
        val response = service.search("returning from a leave of absence notice")
        assertEquals(SearchMode.HYBRID, response.mode)
        assertNull(response.note)
        val ids = response.results.map { it.sectionId }
        assertEquals(ids.distinct(), ids)
        assertTrue(ids.first().endsWith("returning-from-a-leave-of-absence"))
        response.results.forEach { assertTrue(it.citation.path.endsWith(".md")) }
        assertEquals(1, store.lexicalCalls)
        assertEquals(1, store.semanticCalls)
    }

    @Test
    fun `programme filter keeps that programme and university-wide pages only`() = runTest {
        val response = service.search("leave of absence tuition refund", programme = "ECE-MS", topK = 20)
        assertEquals("ece-ms", response.programme)                       // case-insensitive
        val programmes = response.results.map { it.citation.programme }.toSet()
        assertTrue("aice-beng" !in programmes, "got $programmes")
        assertTrue(response.results.any { it.sectionId == "general/policies/tuition-refunds" })
        assertTrue(response.results.any { it.sectionId == "minutes/2026-08-20-gsc" })  // applies_to: [ece-ms]
    }

    @Test
    fun `without a model, hybrid falls back to lexical and says so`() = runTest {
        val lexicalOnly = HandbookService(store, embedder = null)
        val response = lexicalOnly.search("tuition refund")
        assertEquals(SearchMode.LEXICAL, response.mode)
        assertNotNull(response.note)
        assertEquals(0, store.semanticCalls)
    }

    @Test
    fun `lexical and semantic modes use one retriever each`() = runTest {
        service.search("tuition", mode = SearchMode.LEXICAL)
        assertEquals(1 to 0, store.lexicalCalls to store.semanticCalls)
        service.search("tuition", mode = SearchMode.SEMANTIC)
        assertEquals(1 to 1, store.lexicalCalls to store.semanticCalls)
    }

    @Test
    fun `top_k is clamped and bad input is rejected`() = runTest {
        assertEquals(1, service.search("leave", topK = 0).results.size)
        assertFailsWith<InvalidArgumentException> { service.search("   ") }
        assertFailsWith<InvalidArgumentException> { service.search("x".repeat(1001)) }
        val error = assertFailsWith<InvalidArgumentException> { service.search("leave", programme = "law-phd") }
        assertTrue("aice-beng" in error.message!!)
    }

    @Test
    fun `section lookup finds a page or suggests close ids`() = runTest {
        val found = service.section("aice-beng/32-academic-probation.md")
        assertIs<SectionLookup.Found>(found)
        assertEquals("§9.2 Academic Probation", found.section.citation.section)

        val missing = service.section("aice-beng/academic-probation")
        assertIs<SectionLookup.NotFound>(missing)
        assertTrue("aice-beng/32-academic-probation" in missing.suggestions)
    }

    @Test
    fun `recent changes filter by date and programme`() = runTest {
        assertEquals(3, service.recentChanges().size)
        assertEquals(2, service.recentChanges(since = java.time.LocalDate.parse("2026-09-01")).size)
        assertEquals(listOf("ccc3333"), service.recentChanges(programme = "ece-ms").map { it.commit })
    }
}
