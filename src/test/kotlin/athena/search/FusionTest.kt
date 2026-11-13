package athena.search

import athena.domain.Citation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FusionTest {
    private fun hit(chunk: Long, section: String = "s$chunk") =
        ChunkHit(chunk, section, "Title $section", "text", 0.0, Citation("H", "p", "S", "$section.md"))

    @Test
    fun `a chunk found by both retrievers beats one found by only one`() {
        val fused = reciprocalRankFusion(
            mapOf(
                "lexical" to listOf(hit(1), hit(2)),
                "semantic" to listOf(hit(3), hit(2)),
            ),
        )
        assertEquals(listOf(2L, 1L, 3L), fused.map { it.hit.chunkId })
        assertEquals(listOf("lexical", "semantic"), fused.first().matchedBy)
        assertEquals(1.0 / 62 + 1.0 / 62, fused.first().score, 1e-12)
    }

    @Test
    fun `ties are broken by chunk id so results are stable`() {
        val fused = reciprocalRankFusion(mapOf("lexical" to listOf(hit(9)), "semantic" to listOf(hit(4))))
        assertEquals(listOf(4L, 9L), fused.map { it.hit.chunkId })
    }

    @Test
    fun `empty input gives empty output`() {
        assertEquals(emptyList(), reciprocalRankFusion(mapOf("lexical" to emptyList(), "semantic" to emptyList())))
    }

    @Test
    fun `bestPerSection keeps the first chunk of each section`() {
        val fused = reciprocalRankFusion(mapOf("lexical" to listOf(hit(1, "a"), hit(2, "a"), hit(3, "b"), hit(4, "c"))))
        assertEquals(listOf(1L, 3L), fused.bestPerSection(2).map { it.hit.chunkId })
    }

    @Test
    fun `k must be positive`() {
        assertFailsWith<IllegalArgumentException> { reciprocalRankFusion(emptyMap(), k = 0) }
    }
}
