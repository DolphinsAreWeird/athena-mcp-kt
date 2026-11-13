package athena.testing

import athena.domain.ChangeEntry
import athena.domain.Programme
import athena.domain.Section
import athena.embed.Embedder
import athena.embed.cosine
import athena.search.ChunkHit
import athena.search.HandbookStore
import athena.search.StoreStats
import java.time.LocalDate

/** In-memory [HandbookStore] over the fixture OKB, for testing the service, MCP tools and routes without a database. */
class FakeHandbookStore(
    private val embedder: Embedder = HashingEmbedder(),
    val changeLog: List<ChangeEntry> = SAMPLE_CHANGES,
) : HandbookStore {
    private data class Row(val chunkId: Long, val page: FixturePage, val text: String, val vector: FloatArray)

    private val rows: List<Row> = FixtureOkb.pages.flatMap { page ->
        page.chunks().map { chunk -> Row(0, page, chunk.text, embedder.embed("${page.title}. ${chunk.text}")) }
    }.mapIndexed { i, row -> row.copy(chunkId = i + 1L) }

    var lexicalCalls = 0
        private set
    var semanticCalls = 0
        private set

    private fun FixturePage.visibleTo(programme: String?) =
        programme == null || appliesTo.isEmpty() || programme in appliesTo

    private fun Row.toHit(score: Double) = ChunkHit(chunkId, page.id, page.title, text, score, page.citation)

    override suspend fun lexicalSearch(query: String, programme: String?, limit: Int): List<ChunkHit> {
        lexicalCalls++
        val words = words(query)
        return rows.filter { it.page.visibleTo(programme) }
            .map { it to (words(it.page.title + " " + it.text) intersect words).size.toDouble() }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<Row, Double>> { it.second }.thenBy { it.first.chunkId })
            .take(limit)
            .map { (row, score) -> row.toHit(score) }
    }

    override suspend fun semanticSearch(vector: FloatArray, programme: String?, limit: Int): List<ChunkHit> {
        semanticCalls++
        return rows.filter { it.page.visibleTo(programme) }
            .map { it to cosine(vector, it.vector) }
            .sortedWith(compareByDescending<Pair<Row, Double>> { it.second }.thenBy { it.first.chunkId })
            .take(limit)
            .map { (row, score) -> row.toHit(score) }
    }

    override suspend fun section(id: String): Section? =
        FixtureOkb.pages.firstOrNull { it.id == id }?.let { p ->
            Section(p.id, p.title, p.programme, p.type, p.description, p.appliesTo, p.body, p.citation)
        }

    override suspend fun similarSectionIds(id: String, limit: Int): List<String> {
        val words = words(id.replace('-', ' ').replace('/', ' '))
        return FixtureOkb.pages.filter { (words(it.id.replace('-', ' ').replace('/', ' ')) intersect words).isNotEmpty() }
            .map { it.id }
            .take(limit)
    }

    override suspend fun programmes(): List<Programme> =
        FixtureOkb.pages.groupBy { it.programme }
            .map { (code, pages) -> Programme(code, "Programme $code", "$code handbook", pages.size) }
            .sortedBy { it.code }

    override suspend fun changes(since: LocalDate?, programme: String?, limit: Int): List<ChangeEntry> =
        changeLog.filter { since == null || LocalDate.parse(it.date) >= since }
            .filter { programme == null || it.programme == programme }
            .take(limit)

    override suspend fun stats() = StoreStats(FixtureOkb.pages.size, rows.size, rows.size, "abc1234", "2026-11-14T00:00:00+07")

    companion object {
        private val STOP = setOf("the", "a", "an", "of", "to", "from", "in", "is", "how", "what", "must", "be", "for", "and", "before")

        fun words(text: String): Set<String> =
            Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).map { it.value.removeSuffix("s") }.filter { it !in STOP }.toSet()

        val SAMPLE_CHANGES = listOf(
            ChangeEntry("aaa1111", "2026-10-01", "KP", "Approve", "Academic Probation", "aice-beng/32-academic-probation",
                "aice-beng/32-academic-probation.md", "aice-beng", "M"),
            ChangeEntry("bbb2222", "2026-09-24", "Beam", "Add new page", "Generative AI in Coursework",
                "general/generative-ai-in-coursework", "general/generative-ai-in-coursework.md", "general", "A"),
            ChangeEntry("ccc3333", "2026-08-20", "Athena", "Approve", "Returning from a Leave of Absence",
                "ece-ms/23-returning-from-a-leave-of-absence", "ece-ms/23-returning-from-a-leave-of-absence.md", "ece-ms", "M"),
        )
    }
}
