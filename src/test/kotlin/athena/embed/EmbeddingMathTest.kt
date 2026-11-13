package athena.embed

import athena.db.toPgVector
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EmbeddingMathTest {
    @Test
    fun `mean pooling ignores padded tokens`() {
        val tokens = arrayOf(floatArrayOf(1f, 2f), floatArrayOf(3f, 4f), floatArrayOf(100f, 100f))
        assertContentEquals(floatArrayOf(2f, 3f), meanPool(tokens, longArrayOf(1, 1, 0)))
    }

    @Test
    fun `l2 normalisation gives unit length and leaves zero vectors alone`() {
        assertContentEquals(floatArrayOf(0.6f, 0.8f), floatArrayOf(3f, 4f).l2Normalized())
        assertContentEquals(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f).l2Normalized())
    }

    @Test
    fun `cosine similarity`() {
        assertEquals(1.0, cosine(floatArrayOf(1f, 1f), floatArrayOf(2f, 2f)), 1e-9)
        assertEquals(0.0, cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-9)
        assertEquals(1 / sqrt(2.0), cosine(floatArrayOf(1f, 0f), floatArrayOf(1f, 1f)), 1e-6)
    }

    @Test
    fun `pgvector literal`() {
        assertEquals("[0.5,-1.0,0.0]", floatArrayOf(0.5f, -1f, 0f).toPgVector())
    }
}
