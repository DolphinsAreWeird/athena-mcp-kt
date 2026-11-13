package athena.search

import athena.domain.ChangeEntry
import athena.domain.Citation
import athena.domain.Programme
import athena.domain.Section
import java.time.LocalDate

/** A chunk that one retriever returned, already joined with its section's title and citation. */
data class ChunkHit(
    val chunkId: Long,
    val sectionId: String,
    val title: String,
    val text: String,
    /** The retriever's own score (ts_rank or cosine similarity). Only used for ordering within one list. */
    val score: Double,
    val citation: Citation,
)

data class StoreStats(
    val sections: Int,
    val chunks: Int,
    val embeddedChunks: Int,
    val okbCommit: String?,
    val lastIngest: String?,
)

/**
 * Everything the service needs from storage.
 *
 * An interface (not the Postgres class directly) so the MCP tools and HTTP routes can be unit-tested
 * with an in-memory fake, and so the storage could be swapped later.
 */
interface HandbookStore {
    /** Postgres full-text search: words and stems, weighted title > heading > body. */
    suspend fun lexicalSearch(query: String, programme: String?, limit: Int): List<ChunkHit>

    /** pgvector nearest neighbours by cosine distance. */
    suspend fun semanticSearch(vector: FloatArray, programme: String?, limit: Int): List<ChunkHit>

    suspend fun section(id: String): Section?

    /** Section ids that look like `id` — used to suggest alternatives when a lookup misses. */
    suspend fun similarSectionIds(id: String, limit: Int): List<String>

    suspend fun programmes(): List<Programme>

    suspend fun changes(since: LocalDate?, programme: String?, limit: Int): List<ChangeEntry>

    suspend fun stats(): StoreStats
}
