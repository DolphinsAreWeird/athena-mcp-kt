package athena.search

import athena.config.DatabaseConfig
import athena.config.toDatabaseConfig
import athena.db.createDataSource
import athena.db.toPgVector
import athena.domain.SearchMode
import athena.domain.SectionLookup
import athena.testing.FakeHandbookStore
import athena.testing.FixtureOkb
import athena.testing.HashingEmbedder
import athena.testing.repoRoot
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Date
import java.sql.Timestamp
import java.time.OffsetDateTime
import javax.sql.DataSource
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Runs the real SQL (full-text search, pgvector, filters) against a real Postgres with pgvector:
 * - `ATHENA_TEST_DATABASE_URL=postgresql://localhost:5432/athena_test` uses an existing server
 *   (the database name must contain "test": the tables are dropped and recreated), or
 * - Testcontainers starts `pgvector/pgvector:0.8.7-pg17` when Docker is available (CI does this).
 * Without either, the tests are skipped, not failed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresIntegrationTest {
    private var container: PostgreSQLContainer? = null
    private lateinit var writer: HikariDataSource
    private lateinit var reader: HikariDataSource
    private lateinit var service: HandbookService
    private val embedder = HashingEmbedder()

    @BeforeAll
    fun startDatabase() {
        val url = System.getenv("ATHENA_TEST_DATABASE_URL")
        val config: DatabaseConfig = if (url != null) {
            url.toDatabaseConfig().also {
                require("test" in it.jdbcUrl.substringAfterLast('/')) { "Refusing to wipe a database whose name lacks 'test': $url" }
            }
        } else {
            assumeTrue(dockerAvailable(), "Docker not available and ATHENA_TEST_DATABASE_URL not set")
            val image = DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres")
            val started = PostgreSQLContainer(image).also { it.start() }
            container = started
            DatabaseConfig(started.jdbcUrl, started.username, started.password)
        }
        writer = createDataSource(config, readOnly = false)
        seed(writer)
        reader = createDataSource(config, readOnly = true)
        service = HandbookService(PostgresHandbookStore(reader), embedder)
    }

    @AfterAll
    fun stopDatabase() {
        if (::reader.isInitialized) reader.close()
        if (::writer.isInitialized) writer.close()
        container?.stop()
    }

    @Test
    fun `lexical search uses stems and ranks the title match first`() = runTest {
        val response = service.search("refunds for tuition", mode = SearchMode.LEXICAL)
        assertEquals("general/policies/tuition-refunds", response.results.first().sectionId)
        assertEquals(listOf("lexical"), response.results.first().matchedBy)
    }

    @Test
    fun `natural-language questions match on any of their words`() = runTest {
        val response = service.search("How long is the probation for a student whose average falls?", mode = SearchMode.LEXICAL)
        assertEquals("aice-beng/32-academic-probation", response.results.first().sectionId)
    }

    @Test
    fun `semantic search finds the nearest chunks`() = runTest {
        val response = service.search("generative AI tools coursework syllabus", mode = SearchMode.SEMANTIC)
        assertEquals("general/generative-ai-in-coursework", response.results.first().sectionId)
    }

    @Test
    fun `hybrid search with a programme filter, citations intact`() = runTest {
        val response = service.search("notice to return from a leave of absence", programme = "ece-ms", topK = 10)
        val ids = response.results.map { it.sectionId }
        assertEquals("ece-ms/23-returning-from-a-leave-of-absence", ids.first())
        assertTrue("aice-beng/23-returning-from-a-leave-of-absence" !in ids)
        assertTrue("minutes/2026-08-20-gsc" in ids, "minutes that apply to ece-ms are included: $ids")
        val citation = response.results.first().citation
        assertEquals("ece-ms/23-returning-from-a-leave-of-absence.md", citation.path)
        assertEquals("2026-07-15", citation.lastUpdated)
        assertTrue("lexical" in response.results.first().matchedBy && "semantic" in response.results.first().matchedBy)
    }

    @Test
    fun `get_section, suggestions, programmes, changes and stats`() = runTest {
        val found = service.section("aice-beng/32-academic-probation")
        assertIs<SectionLookup.Found>(found)
        assertEquals("§9.2 Academic Probation", found.section.citation.section)

        val missing = service.section("aice-beng/probation")
        assertIs<SectionLookup.NotFound>(missing)
        assertEquals("aice-beng/32-academic-probation", missing.suggestions.first())

        assertEquals(listOf("aice-beng", "ece-ms", "general"), service.programmes().map { it.code })
        assertEquals(listOf("aaa1111", "bbb2222"), service.recentChanges(since = java.time.LocalDate.parse("2026-09-01")).map { it.commit })
        assertEquals(listOf("ccc3333"), service.recentChanges(programme = "ece-ms").map { it.commit })

        val stats = service.stats()
        assertEquals(FixtureOkb.pages.size, stats.sections)
        assertEquals(stats.chunks, stats.embeddedChunks)
        assertEquals("fixture", stats.okbCommit)
    }

    private fun dockerAvailable() = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)

    /** Loads the fixture OKB the way the Python ingest job would (simplified chunking). */
    private fun seed(db: DataSource) {
        db.connection.use { conn ->
            conn.createStatement().use { st ->
                st.execute("DROP TABLE IF EXISTS changes, chunks, sections, programmes, ingest_runs CASCADE")
                st.execute(repoRoot.resolve("db/schema.sql").readText())
            }
            conn.prepareStatement("INSERT INTO programmes (code, name, handbook) VALUES (?, ?, ?)").use { st ->
                FixtureOkb.pages.map { it.programme }.distinct().forEach { code ->
                    st.setString(1, code); st.setString(2, "Programme $code"); st.setString(3, "$code handbook"); st.executeUpdate()
                }
            }
            val sectionSql = """INSERT INTO sections (id, programme, path, title, type, description, section_no, handbook,
                source_document, updated_on, applies_to, body, content_hash) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'x')"""
            val chunkSql = "INSERT INTO chunks (section_id, ord, section_title, heading, text, embedding) VALUES (?,?,?,?,?,?::vector)"
            conn.prepareStatement(sectionSql).use { s ->
                conn.prepareStatement(chunkSql).use { c ->
                    for (p in FixtureOkb.pages) {
                        s.setString(1, p.id); s.setString(2, p.programme); s.setString(3, p.path); s.setString(4, p.title)
                        s.setString(5, p.type); s.setString(6, p.description); s.setString(7, p.sectionNo); s.setString(8, p.handbook)
                        s.setString(9, p.sourceDocument); s.setDate(10, p.updatedOn?.let { Date.valueOf(it) })
                        s.setArray(11, conn.createArrayOf("text", p.appliesTo.toTypedArray())); s.setString(12, p.body)
                        s.executeUpdate()
                        for (chunk in p.chunks()) {
                            c.setString(1, p.id); c.setInt(2, chunk.ord); c.setString(3, p.title); c.setString(4, chunk.heading)
                            c.setString(5, chunk.text); c.setString(6, embedder.embed("${p.title}. ${chunk.text}").toPgVector())
                            c.executeUpdate()
                        }
                    }
                }
            }
            conn.prepareStatement(
                "INSERT INTO changes (commit, committed_at, author, action, title, path, section_id, programme, status) VALUES (?,?,?,?,?,?,?,?,?)",
            ).use { st ->
                for (ch in FakeHandbookStore.SAMPLE_CHANGES) {
                    st.setString(1, ch.commit); st.setTimestamp(2, Timestamp.from(OffsetDateTime.parse("${ch.date}T12:00:00+07:00").toInstant()))
                    st.setString(3, ch.author); st.setString(4, ch.action); st.setString(5, ch.title); st.setString(6, ch.path)
                    st.setString(7, ch.sectionId); st.setString(8, ch.programme); st.setString(9, ch.status)
                    st.executeUpdate()
                }
            }
            conn.createStatement().use {
                it.execute("INSERT INTO ingest_runs (okb_root, okb_commit, model, sections, chunks, embedded) SELECT 'fixtures', 'fixture', 'hashing', (SELECT count(*) FROM sections), (SELECT count(*) FROM chunks), (SELECT count(*) FROM chunks)")
            }
        }
    }
}
