package athena.search

/** A chunk after fusion, with every retriever that found it. */
data class FusedHit(val hit: ChunkHit, val score: Double, val matchedBy: List<String>)

/**
 * Reciprocal Rank Fusion (Cormack et al., 2009): `score(d) = Σ 1 / (k + rank_i(d))` over each ranked list.
 *
 * Why RRF and not "add the scores": ts_rank and cosine similarity live on different, unrelated scales.
 * RRF only uses each list's *ranks*, so neither retriever can drown the other, and a chunk found by
 * both rises to the top. k = 60 is the value from the paper and works well without tuning.
 *
 * @param rankings retriever name -> its hits, best first.
 */
fun reciprocalRankFusion(rankings: Map<String, List<ChunkHit>>, k: Int = 60): List<FusedHit> {
    require(k > 0) { "k must be positive" }
    val scores = mutableMapOf<Long, Double>()
    val sources = mutableMapOf<Long, MutableList<String>>()
    val firstSeen = linkedMapOf<Long, ChunkHit>()

    for ((retriever, hits) in rankings) {
        hits.forEachIndexed { index, hit ->
            val rank = index + 1
            scores.merge(hit.chunkId, 1.0 / (k + rank), Double::plus)
            sources.getOrPut(hit.chunkId) { mutableListOf() }.add(retriever)
            firstSeen.putIfAbsent(hit.chunkId, hit)
        }
    }

    return firstSeen.values
        .map { hit -> FusedHit(hit, scores.getValue(hit.chunkId), sources.getValue(hit.chunkId).distinct()) }
        .sortedWith(compareByDescending<FusedHit> { it.score }.thenBy { it.hit.chunkId })
}

/** Keeps the best chunk of each section, so one long page can't fill every slot. */
fun List<FusedHit>.bestPerSection(limit: Int): List<FusedHit> = distinctBy { it.hit.sectionId }.take(limit)
