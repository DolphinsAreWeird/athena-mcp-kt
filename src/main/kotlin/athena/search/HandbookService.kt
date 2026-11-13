package athena.search

import athena.domain.ChangeEntry
import athena.domain.InvalidArgumentException
import athena.domain.Passage
import athena.domain.Programme
import athena.domain.SearchMode
import athena.domain.SearchResponse
import athena.domain.SectionLookup
import athena.embed.Embedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The one place that knows how to answer handbook questions. The MCP tools and the REST routes are
 * thin adapters over it, so both always behave the same.
 *
 * @param embedder `null` when no model is configured; search then falls back to lexical only.
 */
class HandbookService(
    private val store: HandbookStore,
    private val embedder: Embedder?,
) {
    val semanticAvailable: Boolean get() = embedder != null

    suspend fun search(
        query: String,
        programme: String? = null,
        topK: Int = DEFAULT_TOP_K,
        mode: SearchMode = SearchMode.HYBRID,
    ): SearchResponse {
        val text = query.trim()
        if (text.isEmpty()) throw InvalidArgumentException("query must not be empty")
        if (text.length > MAX_QUERY_LENGTH) throw InvalidArgumentException("query is longer than $MAX_QUERY_LENGTH characters")
        val k = topK.coerceIn(1, MAX_TOP_K)
        val programmeCode = programme?.let { resolveProgramme(it) }

        var effectiveMode = mode
        var note: String? = null
        if (mode != SearchMode.LEXICAL && embedder == null) {
            effectiveMode = SearchMode.LEXICAL
            note = "Semantic search is unavailable (no embedding model configured), so this used lexical search only."
        }

        // Structured concurrency: both retrievers run at the same time; if one fails, the other is
        // cancelled and the exception propagates. Nothing leaks past the `coroutineScope` block.
        val fused = coroutineScope {
            val lexical = async {
                if (effectiveMode == SearchMode.SEMANTIC) emptyList()
                else store.lexicalSearch(text, programmeCode, CANDIDATES)
            }
            val semantic = async {
                if (effectiveMode == SearchMode.LEXICAL) emptyList()
                else store.semanticSearch(embedQuery(text), programmeCode, CANDIDATES)
            }
            reciprocalRankFusion(mapOf("lexical" to lexical.await(), "semantic" to semantic.await()))
        }

        val results = fused.bestPerSection(k).map { (hit, score, matchedBy) ->
            Passage(hit.sectionId, hit.title, hit.text, score, matchedBy, hit.citation)
        }
        return SearchResponse(text, programmeCode, effectiveMode, results, note)
    }

    suspend fun section(id: String): SectionLookup {
        val clean = id.trim().removePrefix("/").removeSuffix(".md")
        if (clean.isEmpty()) throw InvalidArgumentException("id must not be empty")
        // `when` on a nullable: either we found it or we suggest close matches.
        return when (val section = store.section(clean)) {
            null -> SectionLookup.NotFound(clean, store.similarSectionIds(clean, 5))
            else -> SectionLookup.Found(section)
        }
    }

    suspend fun programmes(): List<Programme> = store.programmes()

    suspend fun recentChanges(since: LocalDate? = null, programme: String? = null, limit: Int = 20): List<ChangeEntry> =
        store.changes(since, programme?.let { resolveProgramme(it) }, limit.coerceIn(1, 100))

    suspend fun stats() = store.stats()

    /** Accepts a programme code in any case ("ECE-MS") and rejects unknown ones with the valid list. */
    private suspend fun resolveProgramme(input: String): String {
        val known = store.programmes().map { it.code }
        return known.firstOrNull { it.equals(input.trim(), ignoreCase = true) }
            ?: throw InvalidArgumentException("unknown programme '$input'; use one of: ${known.joinToString()}")
    }

    /** Model inference is CPU work, so it runs on the Default (CPU-sized) pool, not the IO pool. */
    private suspend fun embedQuery(text: String): FloatArray =
        withContext(Dispatchers.Default) { checkNotNull(embedder).embed(text) }

    companion object {
        const val DEFAULT_TOP_K = 5
        const val MAX_TOP_K = 20
        const val MAX_QUERY_LENGTH = 1_000
        /** How many chunks each retriever hands to fusion. */
        const val CANDIDATES = 50
    }
}
